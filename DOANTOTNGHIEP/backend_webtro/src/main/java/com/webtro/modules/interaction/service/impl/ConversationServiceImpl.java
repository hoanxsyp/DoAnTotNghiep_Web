package com.webtro.modules.interaction.service.impl;

import com.webtro.common.PageResponse;
import com.webtro.common.enums.BannedKeywordScope;
import com.webtro.common.enums.ContactType;
import com.webtro.common.event.ContactCreatedEvent;
import com.webtro.constant.ConfigKey;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessRuleException;
import com.webtro.exception.ForbiddenException;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.interaction.dto.request.CreateConversationRequest;
import com.webtro.modules.interaction.dto.request.SendMessageRequest;
import com.webtro.modules.interaction.dto.response.ConversationCreatedResponse;
import com.webtro.modules.interaction.dto.response.ConversationPageResponse;
import com.webtro.modules.interaction.dto.response.ConversationResponse;
import com.webtro.modules.interaction.dto.response.MessageResponse;
import com.webtro.modules.interaction.entity.ContactLog;
import com.webtro.modules.interaction.entity.Conversation;
import com.webtro.modules.interaction.entity.Message;
import com.webtro.modules.interaction.mapper.ConversationMapper;
import com.webtro.modules.interaction.repository.ContactLogRepository;
import com.webtro.modules.interaction.repository.ConversationRepository;
import com.webtro.modules.interaction.repository.MessageRepository;
import com.webtro.modules.interaction.service.ConversationService;
import com.webtro.modules.interaction.spi.BannedKeywordGateway;
import com.webtro.modules.interaction.spi.ListingGateway;
import com.webtro.modules.interaction.spi.UserGateway;
import com.webtro.modules.interaction.websocket.ChatRealtimePublisher;
import com.webtro.security.RateLimitService;
import com.webtro.util.HtmlSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Hiện thực nghiệp vụ hội thoại/tin nhắn (CONT-03) — canonical 4.6.4–4.6.9, {@code [§3.10]}.
 *
 * <p><b>Giới hạn repository (đã chốt):</b> {@code ConversationRepository} chỉ có finder tách theo
 * tenant/landlord, không có finder gộp hay lọc {@code listingId}/{@code unreadOnly}; và
 * {@code MessageRepository} chỉ có finder sắp xếp tăng dần. Do đó role {@code ALL} và các bộ lọc
 * phụ được xử lý ở tầng bộ nhớ (có giới hạn), còn tin nhắn trả về theo thứ tự tăng dần. Muốn lọc
 * ở DB cần bổ sung Specification/@Query cho hai repository này.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private static final int CONVERSATION_MAX_PER_HOUR = 20;
    private static final int MERGE_CAP = 200;
    private static final int READ_SCAN_CAP = 500;
    private static final int PREVIEW_MAX = 200;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ContactLogRepository contactLogRepository;
    private final ListingGateway listingGateway;
    private final UserGateway userGateway;
    private final BannedKeywordGateway bannedKeywordGateway;
    private final ConversationMapper conversationMapper;
    private final ChatRealtimePublisher chatRealtimePublisher;
    private final RateLimitService rateLimitService;
    private final SystemConfigService systemConfig;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public ConversationPageResponse listConversations(Long userId, String role, boolean unreadOnly,
                                                      Long listingId, Pageable pageable) {
        String effectiveRole = role == null ? "ALL" : role;
        List<Conversation> content;
        long total;

        if ("AS_TENANT".equals(effectiveRole)) {
            Page<Conversation> page = conversationRepository
                    .findByTenantIdAndDeletedAtIsNullOrderByLastMessageAtDesc(userId, pageable);
            content = filter(page.getContent(), userId, unreadOnly, listingId);
            total = page.getTotalElements();
        } else if ("AS_LANDLORD".equals(effectiveRole)) {
            Page<Conversation> page = conversationRepository
                    .findByLandlordIdAndDeletedAtIsNullOrderByLastMessageAtDesc(userId, pageable);
            content = filter(page.getContent(), userId, unreadOnly, listingId);
            total = page.getTotalElements();
        } else {
            // ALL: gộp hai nguồn (có giới hạn) + phân trang thủ công.
            List<Conversation> merged = new ArrayList<>();
            merged.addAll(conversationRepository.findByTenantIdAndDeletedAtIsNullOrderByLastMessageAtDesc(
                    userId, PageRequest.of(0, MERGE_CAP)).getContent());
            merged.addAll(conversationRepository.findByLandlordIdAndDeletedAtIsNullOrderByLastMessageAtDesc(
                    userId, PageRequest.of(0, MERGE_CAP)).getContent());
            List<Conversation> filtered = filter(merged, userId, unreadOnly, listingId).stream()
                    .sorted(Comparator.comparing(Conversation::getLastMessageAt,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
            total = filtered.size();
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), filtered.size());
            content = start >= filtered.size() ? List.of() : filtered.subList(start, end);
        }

        List<Long> listingIds = content.stream().map(Conversation::getListingId).distinct().toList();
        Map<Long, ListingGateway.ListingBrief> listings = listingGateway.getBriefs(listingIds);
        List<Long> partnerIds = content.stream()
                .map(c -> c.getTenantId().equals(userId) ? landlordIdOf(c, listings.get(c.getListingId())) : c.getTenantId())
                .filter(Objects::nonNull)
                .distinct().toList();
        Map<Long, UserGateway.UserBrief> partners = userGateway.getBriefs(partnerIds);

        List<ConversationResponse> items = content.stream().map(c -> {
            ListingGateway.ListingBrief listing = listings.get(c.getListingId());
            Long landlordId = landlordIdOf(c, listing);
            Long partnerId = c.getTenantId().equals(userId) ? landlordId : c.getTenantId();
            return conversationMapper.toResponse(c, userId, landlordId, listing, partners.get(partnerId));
        }).toList();

        Page<ConversationResponse> asPage = new PageImpl<>(items, pageable, total);
        long unreadConvs = content.stream().filter(c -> unreadCountFor(c, userId) > 0).count();
        return ConversationPageResponse.builder()
                .page(PageResponse.from(asPage))
                .totalUnreadConversations(unreadConvs)
                .build();
    }

    @Override
    @Transactional
    public ConversationCreatedResponse createConversation(CreateConversationRequest request, Long userId) {
        rateLimitService.checkLimit("conversation", String.valueOf(userId),
                CONVERSATION_MAX_PER_HOUR, Duration.ofHours(1));

        ListingGateway.ListingBrief listing = listingGateway.getBrief(request.getListingId());
        if (!listing.publiclyVisible()) {
            throw new BusinessRuleException(ErrorCode.LISTING_NOT_ACTIVE);
        }
        if (listing.ownerId().equals(userId)) {
            throw new BusinessRuleException(ErrorCode.CONTACT_FORBIDDEN_SELF);
        }
        if (userGateway.isContactRestricted(userId)) {
            throw new ForbiddenException(ErrorCode.CONTACT_RESTRICTED_SPAM);
        }
        if (!userGateway.isLandlordChatEnabled(listing.ownerId())) {
            throw new BusinessRuleException(ErrorCode.CHAT_DISABLED_BY_LANDLORD);
        }
        String message = scanAndSanitize(request.getInitialMessage());

        Persisted persisted = persistConversationMessage(
                request.getListingId(), userId, listing.ownerId(), message);

        // CONT-05: mỗi lần mở chat cũng là một lượt liên hệ.
        writeChatContactLog(listing, userId);

        return ConversationCreatedResponse.builder()
                .id(persisted.conversation().getId())
                .listingId(listing.id())
                .listingTitle(listing.title())
                .tenantId(userId)
                .landlordId(listing.ownerId())
                .myRole("TENANT")
                .alreadyExisted(persisted.alreadyExisted())
                .firstMessageId(persisted.message().getId())
                .createdAt(persisted.conversation().getCreatedAt())
                .build();
    }

    @Override
    @Transactional
    public ConversationCreatedResponse startChatFromContact(Long listingId, Long tenantId, Long landlordId,
                                                            String initialMessage) {
        // Người gọi (ContactService) đã kiểm tra public/self/restricted/chat-enabled + quét từ cấm.
        String message = HtmlSanitizer.stripAllHtml(initialMessage);
        Persisted persisted = persistConversationMessage(listingId, tenantId, landlordId, message);
        return ConversationCreatedResponse.builder()
                .id(persisted.conversation().getId())
                .listingId(listingId)
                .tenantId(tenantId)
                .landlordId(landlordId)
                .myRole("TENANT")
                .alreadyExisted(persisted.alreadyExisted())
                .firstMessageId(persisted.message().getId())
                .createdAt(persisted.conversation().getCreatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationResponse getConversation(Long conversationId, Long userId) {
        Conversation c = requireMember(conversationId, userId);
        ListingGateway.ListingBrief listing = listingGateway.findBrief(c.getListingId()).orElse(null);
        Long landlordId = landlordIdOf(c, listing);
        Long partnerId = c.getTenantId().equals(userId) ? landlordId : c.getTenantId();
        UserGateway.UserBrief partner = partnerId == null ? null : userGateway.findBrief(partnerId).orElse(null);
        return conversationMapper.toResponse(c, userId, landlordId, listing, partner);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MessageResponse> listMessages(Long conversationId, Long userId, Pageable pageable) {
        requireMember(conversationId, userId);
        Page<Message> page = messageRepository
                .findByConversation_IdAndDeletedAtIsNullOrderByCreatedAtAsc(conversationId, pageable);
        List<Long> senderIds = page.getContent().stream().map(Message::getSenderId).distinct().toList();
        Map<Long, UserGateway.UserBrief> senders = userGateway.getBriefs(senderIds);
        return PageResponse.from(page, m ->
                conversationMapper.toMessageResponse(m, userId, senders.get(m.getSenderId())));
    }

    @Override
    @Transactional
    public MessageResponse sendMessage(Long conversationId, SendMessageRequest request, Long userId) {
        Conversation c = requireMember(conversationId, userId);
        if (userGateway.isContactRestricted(userId)) {
            throw new ForbiddenException(ErrorCode.CONTACT_RESTRICTED_SPAM);
        }
        int perMinute = systemConfig.getInt(ConfigKey.SPAM_MESSAGE_PER_MINUTE);
        rateLimitService.checkLimit("message", String.valueOf(userId), perMinute, Duration.ofMinutes(1));

        String content = scanAndSanitize(request.getContent());
        Message message = appendMessage(c, userId, content);

        UserGateway.UserBrief sender = userGateway.findBrief(userId).orElse(null);
        return conversationMapper.toMessageResponse(message, userId, sender);
    }

    @Override
    @Transactional
    public void markRead(Long conversationId, Long userId) {
        Conversation c = requireMember(conversationId, userId);
        Instant now = Instant.now();
        Page<Message> page = messageRepository.findByConversation_IdAndDeletedAtIsNullOrderByCreatedAtAsc(
                conversationId, PageRequest.of(0, READ_SCAN_CAP));
        for (Message m : page.getContent()) {
            if (!m.getSenderId().equals(userId) && Boolean.FALSE.equals(m.getIsRead())) {
                m.setIsRead(true);
                m.setReadAt(now);
            }
        }
        messageRepository.saveAll(page.getContent());
        if (c.getTenantId().equals(userId)) {
            c.setTenantUnreadCount(0);
        } else {
            c.setLandlordUnreadCount(0);
        }
        conversationRepository.save(c);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnreadMessages(Long userId) {
        // Bộ đếm chưa đọc được duy trì sẵn trên mỗi hội thoại (tenant_unread_count /
        // landlord_unread_count) — cộng dồn theo cả hai vai trò. Giới hạn quét như các luồng khác.
        long asTenant = conversationRepository
                .findByTenantIdAndDeletedAtIsNullOrderByLastMessageAtDesc(userId, PageRequest.of(0, MERGE_CAP))
                .getContent().stream()
                .mapToLong(Conversation::getTenantUnreadCount)
                .sum();
        long asLandlord = conversationRepository
                .findByLandlordIdAndDeletedAtIsNullOrderByLastMessageAtDesc(userId, PageRequest.of(0, MERGE_CAP))
                .getContent().stream()
                .mapToLong(Conversation::getLandlordUnreadCount)
                .sum();
        return asTenant + asLandlord;
    }

    @Override
    @Transactional(readOnly = true)
    public void assertConversationMember(Long conversationId, Long userId) {
        requireMember(conversationId, userId);
    }

    // ------------------------------------------------------------------

    private record Persisted(Conversation conversation, Message message, boolean alreadyExisted) {
    }

    /** Tạo hoặc lấy lại hội thoại theo bộ ba rồi gắn một tin nhắn. */
    private Persisted persistConversationMessage(Long listingId, Long tenantId, Long landlordId, String message) {
        Conversation conversation = conversationRepository
                .findByListingIdAndTenantIdAndDeletedAtIsNull(listingId, tenantId)
                .orElse(null);
        boolean alreadyExisted = conversation != null;
        if (conversation == null) {
            conversation = conversationRepository.save(Conversation.builder()
                    .listingId(listingId)
                    .tenantId(tenantId)
                    .build());
        }
        Message msg = appendMessage(conversation, tenantId, message);
        return new Persisted(conversation, msg, alreadyExisted);
    }

    /** Gắn tin nhắn, cập nhật bộ đếm/mốc hội thoại và {@code first_response_at} (idempotent). */
    private Message appendMessage(Conversation c, Long senderId, String content) {
        Message message = messageRepository.save(Message.builder()
                .conversation(c)
                .senderId(senderId)
                .content(content)
                .isRead(false)
                .build());

        Instant now = message.getCreatedAt() != null ? message.getCreatedAt() : Instant.now();
        c.setLastMessageAt(now);
        c.setLastMessagePreview(content.length() > PREVIEW_MAX ? content.substring(0, PREVIEW_MAX) : content);
        c.setMessageCount(c.getMessageCount() + 1);
        Long landlordId = landlordIdOf(c);
        if (c.getTenantId().equals(senderId)) {
            c.setLandlordUnreadCount(c.getLandlordUnreadCount() + 1);
        } else if (landlordId != null && landlordId.equals(senderId)) {
            c.setTenantUnreadCount(c.getTenantUnreadCount() + 1);
            // Chủ trọ phản hồi lần đầu → ghi mốc SLA (idempotent) — [§5.7].
            if (c.getFirstResponseAt() == null) {
                c.setFirstResponseAt(now);
            }
        } else {
            throw new ForbiddenException(ErrorCode.CONVERSATION_FORBIDDEN);
        }
        conversationRepository.save(c);
        UserGateway.UserBrief sender = userGateway.findBrief(senderId).orElse(null);
        MessageResponse realtimeMessage = conversationMapper.toMessageResponse(message, senderId, sender);
        publishAfterCommit(c.getId(), realtimeMessage);
        return message;
    }

    private void publishAfterCommit(Long conversationId, MessageResponse message) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            chatRealtimePublisher.publishMessage(conversationId, message);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                chatRealtimePublisher.publishMessage(conversationId, message);
            }
        });
    }

    /** Ghi ContactLog CHAT (dedup theo contact.dedup_minutes) + báo chủ trọ khi được tính. */
    private void writeChatContactLog(ListingGateway.ListingBrief listing, Long tenantId) {
        int dedupMinutes = systemConfig.getInt(ConfigKey.CONTACT_DEDUP_MINUTES);
        Instant since = Instant.now().minus(Duration.ofMinutes(dedupMinutes));
        boolean counted = !contactLogRepository
                .existsByListingIdAndUserIdAndCreatedAtAfterAndDeletedAtIsNull(listing.id(), tenantId, since);
        contactLogRepository.save(ContactLog.builder()
                .listingId(listing.id())
                .userId(tenantId)
                .contactType(ContactType.CHAT)
                .isCounted(counted)
                .isReadByOwner(false)
                .build());
        if (counted) {
            listingGateway.incrementContactCount(listing.id());
            eventPublisher.publishEvent(new ContactCreatedEvent(listing.id(), listing.ownerId(), tenantId));
        }
    }

    private String scanAndSanitize(String text) {
        BannedKeywordGateway.ScanResult scan = bannedKeywordGateway.scan(text, BannedKeywordScope.BOTH);
        if (!scan.isClean()) {
            throw new BusinessRuleException(ErrorCode.BANNED_KEYWORD_DETECTED,
                    "Nội dung chứa từ ngữ không được phép: " + String.join(", ", scan.matched()));
        }
        return HtmlSanitizer.stripAllHtml(text);
    }

    private Conversation requireMember(Long conversationId, Long userId) {
        Conversation c = conversationRepository.findById(conversationId)
                .filter(cv -> cv.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONVERSATION_NOT_FOUND));
        Long landlordId = landlordIdOf(c);
        if (!c.getTenantId().equals(userId) && (landlordId == null || !landlordId.equals(userId))) {
            throw new ForbiddenException(ErrorCode.CONVERSATION_FORBIDDEN);
        }
        return c;
    }

    private int unreadCountFor(Conversation c, Long userId) {
        return c.getTenantId().equals(userId) ? c.getTenantUnreadCount() : c.getLandlordUnreadCount();
    }

    private List<Conversation> filter(List<Conversation> list, Long userId, boolean unreadOnly, Long listingId) {
        return list.stream()
                .filter(c -> listingId == null || listingId.equals(c.getListingId()))
                .filter(c -> !unreadOnly || unreadCountFor(c, userId) > 0)
                .toList();
    }

    private Long landlordIdOf(Conversation c) {
        return listingGateway.findBrief(c.getListingId())
                .map(ListingGateway.ListingBrief::ownerId)
                .orElse(null);
    }

    private Long landlordIdOf(Conversation c, ListingGateway.ListingBrief listing) {
        if (listing != null) {
            return listing.ownerId();
        }
        return landlordIdOf(c);
    }
}
