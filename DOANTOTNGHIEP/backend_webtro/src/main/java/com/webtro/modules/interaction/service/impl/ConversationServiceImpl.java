package com.webtro.modules.interaction.service.impl;

import com.webtro.common.PageResponse;
import com.webtro.common.enums.BannedKeywordScope;
import com.webtro.constant.ConfigKey;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessException;
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
import com.webtro.modules.interaction.entity.Conversation;
import com.webtro.modules.interaction.entity.Message;
import com.webtro.modules.interaction.mapper.ConversationMapper;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Hiện thực nghiệp vụ hội thoại/tin nhắn (CONT-03) — canonical 4.6.4–4.6.9, {@code [§3.10]}.
 *
 * <p>Membership, role, listing and unread filters are applied by the repository before pagination,
 * keeping page totals and unread counters consistent for large inboxes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private static final int CONVERSATION_MAX_PER_HOUR = 20;
    private static final int READ_SCAN_CAP = 500;
    private static final int PREVIEW_MAX = 200;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ListingGateway listingGateway;
    private final UserGateway userGateway;
    private final BannedKeywordGateway bannedKeywordGateway;
    private final ConversationMapper conversationMapper;
    private final ChatRealtimePublisher chatRealtimePublisher;
    private final RateLimitService rateLimitService;
    private final SystemConfigService systemConfig;

    @Override
    @Transactional(readOnly = true)
    public ConversationPageResponse listConversations(Long userId, String role, boolean unreadOnly,
                                                      Long listingId, Pageable pageable) {
        String effectiveRole = switch (role == null ? "ALL" : role) {
            case "AS_TENANT" -> "AS_TENANT";
            case "AS_LANDLORD" -> "AS_LANDLORD";
            default -> "ALL";
        };
        Page<Conversation> page = conversationRepository.findAccessible(
                userId, effectiveRole, unreadOnly, listingId, pageable);
        List<Conversation> content = page.getContent();

        List<Long> listingIds = content.stream().map(Conversation::getListingId).distinct().toList();
        Map<Long, ListingGateway.ListingBrief> listings = listingGateway.getBriefs(listingIds);
        List<Long> partnerIds = content.stream()
                .map(c -> c.getTenantId().equals(userId) ? landlordIdOf(c, listings.get(c.getListingId())) : c.getTenantId())
                .filter(Objects::nonNull)
                .distinct().toList();
        Map<Long, UserGateway.UserBrief> partners = userGateway.getBriefs(partnerIds);

        return ConversationPageResponse.builder()
                .page(PageResponse.from(page, c -> {
                    ListingGateway.ListingBrief listing = listings.get(c.getListingId());
                    Long landlordId = landlordIdOf(c, listing);
                    Long partnerId = c.getTenantId().equals(userId) ? landlordId : c.getTenantId();
                    return conversationMapper.toResponse(c, userId, landlordId, listing, partners.get(partnerId));
                }))
                .totalUnreadConversations(conversationRepository.countUnreadConversations(userId))
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
        // Sort lấy từ pageable (mặc định createdAt ASC ở controller); client lấy trang mới nhất bằng
        // sort=createdAt,desc rồi tự đảo lại để hiển thị.
        Page<Message> page = messageRepository.findByConversation_IdAndDeletedAtIsNull(conversationId, pageable);
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
        // Đồng bộ số chưa đọc sang các tab/thiết bị khác của cùng người dùng.
        publishConversationUpdatesAfterCommit(c);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnreadMessages(Long userId) {
        return conversationRepository.sumUnreadMessages(userId);
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
        int maxLength = Math.min(2000, Math.max(1,
                systemConfig.getInt(ConfigKey.CHAT_MESSAGE_MAX_LENGTH)));
        if (content == null || content.isBlank() || content.length() > maxLength) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Tin nhắn phải có từ 1 đến " + maxLength + " ký tự");
        }
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
        Long conversationId = c.getId();
        runAfterCommit(() -> chatRealtimePublisher.publishMessage(conversationId, realtimeMessage));
        publishConversationUpdatesAfterCommit(c);
        return message;
    }

    /**
     * {@code CONVERSATION_UPDATED} cho cả người thuê và chủ trọ. Dữ liệu (hội thoại theo góc nhìn từng
     * người + tổng chưa đọc) dựng NGAY trong transaction để thấy bộ đếm vừa cập nhật; chỉ GỬI sau commit
     * để không phát sự kiện cho dữ liệu bị rollback.
     */
    private void publishConversationUpdatesAfterCommit(Conversation c) {
        ListingGateway.ListingBrief listing = listingGateway.findBrief(c.getListingId()).orElse(null);
        Long landlordId = landlordIdOf(c, listing);
        List<Long> members = Stream.of(c.getTenantId(), landlordId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, UserGateway.UserBrief> briefs = userGateway.getBriefs(members);

        List<Runnable> sends = new ArrayList<>();
        for (Long userId : members) {
            Long partnerId = c.getTenantId().equals(userId) ? landlordId : c.getTenantId();
            ConversationResponse view = conversationMapper.toResponse(
                    c, userId, landlordId, listing, partnerId == null ? null : briefs.get(partnerId));
            long totalUnread = countUnreadMessages(userId);
            sends.add(() -> chatRealtimePublisher.publishConversationUpdate(userId, view, totalUnread));
        }
        runAfterCommit(() -> sends.forEach(Runnable::run));
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
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
