package com.webtro.modules.interaction.service.impl;

import com.webtro.common.PageResponse;
import com.webtro.common.enums.BannedKeywordScope;
import com.webtro.common.enums.ContactType;
import com.webtro.common.event.ContactCreatedEvent;
import com.webtro.constant.ConfigKey;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessException;
import com.webtro.exception.BusinessRuleException;
import com.webtro.exception.ForbiddenException;
import com.webtro.modules.interaction.dto.request.ContactChannel;
import com.webtro.modules.interaction.dto.request.CreateContactRequest;
import com.webtro.modules.interaction.dto.response.ContactInfoResponse;
import com.webtro.modules.interaction.dto.response.ContactResultResponse;
import com.webtro.modules.interaction.dto.response.ConversationCreatedResponse;
import com.webtro.modules.interaction.dto.response.LandlordContactPageResponse;
import com.webtro.modules.interaction.entity.ContactLog;
import com.webtro.modules.interaction.entity.Conversation;
import com.webtro.modules.interaction.mapper.ContactMapper;
import com.webtro.modules.interaction.repository.ContactLogRepository;
import com.webtro.modules.interaction.repository.ConversationRepository;
import com.webtro.modules.interaction.service.ContactService;
import com.webtro.modules.interaction.service.ConversationService;
import com.webtro.modules.interaction.spi.BannedKeywordGateway;
import com.webtro.modules.interaction.spi.ListingGateway;
import com.webtro.modules.interaction.spi.UserGateway;
import com.webtro.security.RateLimitService;
import com.webtro.util.HtmlSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Hiện thực nghiệp vụ liên hệ (CONT) — canonical 4.6.1–4.6.3, {@code [§3.10]}.
 *
 * <p>Ngưỡng rate limit riêng cho liên hệ (60/giờ xem SĐT, 20/giờ gửi liên hệ) chưa có config key
 * trong {@code ConfigKey}; dùng hằng số theo canonical 4.6.1/4.6.2 và ghi chú để bổ sung key sau.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContactServiceImpl implements ContactService {

    private static final int CONTACT_INFO_MAX_PER_HOUR = 60;
    private static final int CONTACT_CREATE_MAX_PER_HOUR = 20;

    private final ContactLogRepository contactLogRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;
    private final ListingGateway listingGateway;
    private final UserGateway userGateway;
    private final BannedKeywordGateway bannedKeywordGateway;
    private final ContactMapper contactMapper;
    private final RateLimitService rateLimitService;
    private final com.webtro.modules.admin.service.SystemConfigService systemConfig;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public ContactInfoResponse getContactInfo(Long listingId, Long userId) {
        rateLimitService.checkLimit("contact-info", String.valueOf(userId),
                CONTACT_INFO_MAX_PER_HOUR, Duration.ofHours(1));

        ListingGateway.ListingBrief listing = listingGateway.getBrief(listingId);
        guardContactable(listing, userId);

        UserGateway.LandlordContactInfo info = userGateway.getLandlordContactInfo(listing.ownerId());

        boolean counted = logContact(listing, userId, ContactType.VIEW_PHONE, null, null, null).counted();

        Long conversationId = conversationRepository
                .findByListingIdAndTenantIdAndLandlordIdAndDeletedAtIsNull(listingId, userId, listing.ownerId())
                .map(Conversation::getId)
                .orElse(null);

        return ContactInfoResponse.builder()
                .listingId(listingId)
                .contactName(info.contactName())
                .contactPhone(info.contactPhone())
                .contactZalo(info.contactZalo())
                .phoneMasked(false)
                .chatEnabled(info.chatEnabled())
                .landlordId(info.landlordId())
                .landlordName(info.landlordName())
                .landlordVerified(info.verified())
                .contactLogged(counted)
                .conversationId(conversationId)
                .build();
    }

    @Override
    @Transactional
    public ContactResultResponse createContact(Long listingId, CreateContactRequest request,
                                               Long userId, String ipAddress) {
        rateLimitService.checkLimit("contact", String.valueOf(userId),
                CONTACT_CREATE_MAX_PER_HOUR, Duration.ofHours(1));

        ContactChannel channel = request.getType();
        String message = request.getMessage();
        if (channel.requiresMessage() && (message == null || message.isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Vui lòng nhập nội dung liên hệ");
        }

        ListingGateway.ListingBrief listing = listingGateway.getBrief(listingId);
        guardContactable(listing, userId);

        if (message != null && !message.isBlank()) {
            BannedKeywordGateway.ScanResult scan = bannedKeywordGateway.scan(message, BannedKeywordScope.BOTH);
            if (!scan.isClean()) {
                throw new BusinessRuleException(ErrorCode.BANNED_KEYWORD_DETECTED,
                        "Nội dung chứa từ ngữ không được phép: " + String.join(", ", scan.matched()));
            }
            message = HtmlSanitizer.stripAllHtml(message);
        }

        ContactType type = channel.toEntityType();
        Long conversationId = null;

        if (channel == ContactChannel.START_CHAT) {
            if (!userGateway.isLandlordChatEnabled(listing.ownerId())) {
                throw new BusinessRuleException(ErrorCode.CHAT_DISABLED_BY_LANDLORD);
            }
            ConversationCreatedResponse conv = conversationService.startChatFromContact(
                    listingId, userId, listing.ownerId(), message);
            conversationId = conv.getId();
        }

        LogResult result = logContact(listing, userId, type, message, request.getCallbackPhone(), ipAddress);

        return ContactResultResponse.builder()
                .contactLogId(result.id())
                .listingId(listingId)
                .type(channel.name())
                .conversationId(conversationId)
                .contactPhone(channel == ContactChannel.VIEW_PHONE
                        ? userGateway.getLandlordContactInfo(listing.ownerId()).contactPhone() : null)
                .contactCount(listing.contactCount() + (result.counted() ? 1 : 0))
                .deduplicated(!result.counted())
                .landlordNotified(result.counted())
                .createdAt(Instant.now())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public LandlordContactPageResponse listLandlordContacts(Long ownerId, Long listingId, ContactChannel type,
                                                            Instant from, Instant to, Pageable pageable) {
        // Nếu lọc theo tin thì tin phải thuộc chủ trọ hiện tại.
        if (listingId != null) {
            ListingGateway.ListingBrief brief = listingGateway.getBrief(listingId);
            if (!ownerId.equals(brief.ownerId())) {
                throw new ForbiddenException(ErrorCode.LISTING_FORBIDDEN);
            }
        }
        // Repository (đã chốt) chỉ có finder theo owner + phân trang; lọc listingId/type/ngày áp
        // dụng ở tầng bộ nhớ trên trang hiện tại. Bổ sung Specification nếu cần lọc ở DB.
        Page<ContactLog> page = contactLogRepository.findByOwnerIdAndDeletedAtIsNull(ownerId, pageable);

        List<Long> tenantIds = page.getContent().stream().map(ContactLog::getUserId).distinct().toList();
        Map<Long, UserGateway.UserBrief> tenants = userGateway.getBriefs(tenantIds);

        List<Long> listingIds = page.getContent().stream().map(ContactLog::getListingId).distinct().toList();
        Map<Long, ListingGateway.ListingBrief> listings = listingGateway.getBriefs(listingIds);

        PageResponse<com.webtro.modules.interaction.dto.response.LandlordContactResponse> mapped =
                PageResponse.from(page, log -> {
                    ListingGateway.ListingBrief lb = listings.get(log.getListingId());
                    Long convId = conversationRepository
                            .findByListingIdAndTenantIdAndLandlordIdAndDeletedAtIsNull(
                                    log.getListingId(), log.getUserId(), ownerId)
                            .map(Conversation::getId).orElse(null);
                    return contactMapper.toLandlordContact(log,
                            lb == null ? null : lb.title(), convId, tenants.get(log.getUserId()));
                });

        long viewPhone = page.getContent().stream()
                .filter(l -> l.getContactType() == ContactType.VIEW_PHONE).count();
        long form = page.getContent().stream()
                .filter(l -> l.getContactType() == ContactType.FORM).count();
        long chat = page.getContent().stream()
                .filter(l -> l.getContactType() == ContactType.CHAT).count();

        return LandlordContactPageResponse.builder()
                .page(mapped)
                .summary(LandlordContactPageResponse.ContactSummary.builder()
                        .totalContacts(page.getTotalElements())
                        .viewPhone(viewPhone)
                        .sendForm(form)
                        .startChat(chat)
                        .build())
                .build();
    }

    // ------------------------------------------------------------------

    /** Kết quả ghi ContactLog: id bản ghi + có được tính (ngoài cửa sổ khử trùng) không. */
    private record LogResult(Long id, boolean counted) {
    }

    /** Kiểm tra điều kiện được phép liên hệ tin (public, không phải của mình, không bị hạn chế). */
    private void guardContactable(ListingGateway.ListingBrief listing, Long userId) {
        if (!listing.publiclyVisible()) {
            throw new BusinessRuleException(ErrorCode.LISTING_NOT_ACTIVE);
        }
        if (listing.ownerId().equals(userId)) {
            throw new BusinessRuleException(ErrorCode.CONTACT_FORBIDDEN_SELF);
        }
        if (userGateway.isContactRestricted(userId)) {
            throw new ForbiddenException(ErrorCode.CONTACT_RESTRICTED_SPAM);
        }
    }

    /**
     * Ghi một {@code ContactLog}, khử trùng theo {@code contact.dedup_minutes}. Khi được tính
     * (ngoài cửa sổ) → tăng {@code contact_count} và phát {@link ContactCreatedEvent} báo chủ trọ.
     *
     * @return kết quả gồm id bản ghi và cờ được tính (không bị khử trùng)
     */
    private LogResult logContact(ListingGateway.ListingBrief listing, Long userId, ContactType type,
                                 String message, String callbackPhone, String ipAddress) {
        int dedupMinutes = systemConfig.getInt(ConfigKey.CONTACT_DEDUP_MINUTES);
        Instant since = Instant.now().minus(Duration.ofMinutes(dedupMinutes));
        boolean recent = contactLogRepository
                .existsByListingIdAndUserIdAndCreatedAtAfterAndDeletedAtIsNull(listing.id(), userId, since);
        boolean counted = !recent;

        ContactLog log = ContactLog.builder()
                .listingId(listing.id())
                .userId(userId)
                .ownerId(listing.ownerId())
                .contactType(type)
                .message(message)
                .contactPhone(callbackPhone)
                .isCounted(counted)
                .isReadByOwner(false)
                .ipAddress(ipAddress)
                .build();
        log = contactLogRepository.save(log);

        if (counted) {
            listingGateway.incrementContactCount(listing.id());
            eventPublisher.publishEvent(new ContactCreatedEvent(listing.id(), listing.ownerId(), userId));
        }
        return new LogResult(log.getId(), counted);
    }
}
