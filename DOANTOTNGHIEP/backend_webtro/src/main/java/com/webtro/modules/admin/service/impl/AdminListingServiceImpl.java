package com.webtro.modules.admin.service.impl;

import com.webtro.common.BulkActionResponse;
import com.webtro.common.PageResponse;
import com.webtro.common.enums.AuditAction;
import com.webtro.common.enums.ListingStatus;
import com.webtro.common.enums.ModerationActionType;
import com.webtro.common.enums.ModerationResult;
import com.webtro.common.enums.NotificationType;
import com.webtro.common.enums.ReportSeverity;
import com.webtro.common.enums.ReportTargetType;
import com.webtro.common.event.ListingApprovedEvent;
import com.webtro.constant.ConfigKey;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessException;
import com.webtro.exception.ConflictException;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.admin.dto.request.ApproveListingRequest;
import com.webtro.modules.admin.dto.request.BulkListingActionRequest;
import com.webtro.modules.admin.dto.request.LockListingRequest;
import com.webtro.modules.admin.dto.request.RejectListingRequest;
import com.webtro.modules.admin.dto.request.UnlockListingRequest;
import com.webtro.modules.admin.dto.response.AdminListingActionResponse;
import com.webtro.modules.admin.dto.response.AdminListingResponse;
import com.webtro.modules.admin.mapper.AdminListingMapper;
import com.webtro.modules.admin.service.AdminListingService;
import com.webtro.modules.admin.service.AuditLogService;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.admin.specification.AdminListingSpecifications;
import com.webtro.modules.listing.entity.Listing;
import com.webtro.modules.listing.repository.ListingRepository;
import com.webtro.modules.listing.service.ListingCategoryCountPublisher;
import com.webtro.modules.listing.service.ListingOwnerStatsPublisher;
import com.webtro.modules.listing.statemachine.ListingEvent;
import com.webtro.modules.listing.statemachine.ListingStateMachine;
import com.webtro.modules.moderation.entity.ModerationAction;
import com.webtro.modules.moderation.repository.ModerationActionRepository;
import com.webtro.modules.moderation.repository.ReportRepository;
import com.webtro.modules.moderation.spi.ListingModerationGateway;
import com.webtro.modules.notification.service.NotificationService;
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

/**
 * Cài đặt {@link AdminListingService}. Mọi chuyển trạng thái đi qua {@link ListingStateMachine};
 * ghi {@code moderation_actions} + {@code audit_logs}; thông báo chủ trọ; phát
 * {@link ListingApprovedEvent} khi duyệt để module notification báo cho người theo dõi.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminListingServiceImpl implements AdminListingService {

    private final ListingRepository listingRepository;
    private final ListingStateMachine listingStateMachine;
    private final ListingModerationGateway listingModerationGateway;
    private final ModerationActionRepository moderationActionRepository;
    private final ReportRepository reportRepository;
    private final SystemConfigService systemConfigService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final AdminListingMapper adminListingMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ListingCategoryCountPublisher categoryCountPublisher;
    private final ListingOwnerStatsPublisher ownerStatsPublisher;

    /**
     * Tự tham chiếu qua proxy để mỗi phần tử trong thao tác hàng loạt chạy trong một giao dịch
     * riêng ({@code @Transactional} chỉ có hiệu lực khi gọi qua proxy, không phải self-invocation).
     * Tiêm {@code @Lazy} để tránh vòng khởi tạo bean.
     */
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private AdminListingService self;

    // ============================ Danh sách ============================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminListingResponse> searchListings(List<ListingStatus> statuses, String keyword,
                                                             Long ownerId, Long categoryId, Long provinceId,
                                                             Long districtId, Long wardId,
                                                             Boolean priceDeviationFlagged,
                                                             Instant from, Instant to, Pageable pageable) {
        Page<Listing> page = listingRepository.findAll(
                AdminListingSpecifications.filter(statuses, keyword, ownerId, categoryId, provinceId,
                        districtId, wardId, priceDeviationFlagged, from, to), pageable);
        return PageResponse.from(page, l -> adminListingMapper.toResponse(l, reportCount(l.getId())));
    }

    private Long reportCount(Long listingId) {
        return reportRepository.countByTargetTypeAndTargetIdAndDeletedAtIsNull(
                ReportTargetType.LISTING, listingId);
    }

    // ============================ Hàng đợi kiểm duyệt ============================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminListingResponse> moderationQueue(Pageable pageable) {
        // Tin chờ xử lý: PENDING (chờ duyệt) + NEED_REVIEW (bị gắn cờ). Ưu tiên tin cũ trước do
        // controller đặt sort mặc định createdAt ASC.
        List<ListingStatus> statuses = List.of(ListingStatus.PENDING, ListingStatus.NEED_REVIEW);
        Page<Listing> page = listingRepository.findAll(
                AdminListingSpecifications.filter(statuses, null, null, null, null, null, null,
                        null, null, null), pageable);
        return PageResponse.from(page, l -> adminListingMapper.toResponse(l, reportCount(l.getId())));
    }

    // ============================ Chi tiết ============================

    @Override
    @Transactional(readOnly = true)
    public AdminListingResponse getListingDetail(Long id) {
        Listing listing = getAnyListing(id);
        return adminListingMapper.toResponse(listing, reportCount(id));
    }

    // ============================ Duyệt ============================

    @Override
    @Transactional
    public AdminListingActionResponse approve(Long id, ApproveListingRequest request, Long actorId) {
        Listing listing = getAnyListing(id);
        if (listing.getStatus() == ListingStatus.ACTIVE) {
            throw new ConflictException(ErrorCode.LISTING_ALREADY_APPROVED);
        }
        ListingStatus previous = listing.getStatus();
        ListingCategoryCountPublisher.Snapshot categoryCountBefore = categoryCountPublisher.snapshot(listing);
        ListingOwnerStatsPublisher.Snapshot ownerStatsBefore = ownerStatsPublisher.snapshot(listing);
        ListingStatus newStatus = listingStateMachine.transition(previous, ListingEvent.APPROVE);

        int displayDays = request.getDisplayDays() != null
                ? request.getDisplayDays()
                : systemConfigService.getInt(ConfigKey.LISTING_DISPLAY_DAYS);
        Instant now = Instant.now();

        listing.setStatus(newStatus);
        listing.setPublishedAt(now);
        listing.setExpiredAt(now.plus(Duration.ofDays(displayDays)));
        listing.setRejectReason(null);
        listingRepository.save(listing);
        categoryCountPublisher.publishIfChanged(categoryCountBefore, listing);
        ownerStatsPublisher.publishIfChanged(ownerStatsBefore, listing);

        Long modId = recordModeration(listing, actorId, ModerationActionType.APPROVE, null,
                sanitize(request.getNote(), "Duyệt tin"), previous, newStatus);
        Long auditId = auditLogService.recordChange(AuditAction.LISTING_APPROVE, actorId, "LISTING",
                id, listing.getTitle(), previous.name(), newStatus.name(), request.getNote());

        notificationService.notifyUser(listing.getOwnerId(), NotificationType.LISTING_APPROVED,
                "Tin đăng của bạn đã được duyệt", "Tin \"" + listing.getTitle() + "\" đã hiển thị công khai.");
        eventPublisher.publishEvent(new ListingApprovedEvent(id, listing.getOwnerId()));

        return AdminListingActionResponse.builder()
                .id(id).status(newStatus.name()).previousStatus(previous.name())
                .displayDays(displayDays).publishedAt(now).expiredAt(listing.getExpiredAt())
                .moderatorId(actorId).moderationActionId(modId).auditLogId(auditId)
                .ownerNotified(true).at(now).build();
    }

    // ============================ Từ chối ============================

    @Override
    @Transactional
    public AdminListingActionResponse reject(Long id, RejectListingRequest request, Long actorId) {
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessException(ErrorCode.REJECT_REASON_REQUIRED, "Vui lòng nhập lý do từ chối tin");
        }
        Listing listing = getAnyListing(id);
        ListingStatus previous = listing.getStatus();
        ListingStatus newStatus = listingStateMachine.transition(previous, ListingEvent.REJECT);

        String reason = HtmlSanitizer.stripAllHtml(request.getReason());
        listing.setStatus(newStatus);
        listing.setRejectReason(reason);
        listingRepository.save(listing);

        Long modId = recordModeration(listing, actorId, ModerationActionType.REJECT, null,
                reason, previous, newStatus);
        Long auditId = auditLogService.recordChange(AuditAction.LISTING_REJECT, actorId, "LISTING",
                id, listing.getTitle(), previous.name(), newStatus.name(), reason);

        notificationService.notifyUser(listing.getOwnerId(), NotificationType.LISTING_REJECTED,
                "Tin đăng của bạn bị từ chối", "Lý do: " + reason);

        return AdminListingActionResponse.builder()
                .id(id).status(newStatus.name()).previousStatus(previous.name())
                .reason(reason).reasonCode(request.getReasonCode())
                .moderatorId(actorId).moderationActionId(modId).auditLogId(auditId)
                .ownerNotified(true).at(Instant.now()).build();
    }

    // ============================ Khóa ============================

    @Override
    @Transactional
    public AdminListingActionResponse lock(Long id, LockListingRequest request, Long actorId) {
        if (request.getReason() == null || request.getReason().isBlank() || request.getSeverity() == null) {
            throw new BusinessException(ErrorCode.LOCK_LISTING_REASON_REQUIRED,
                    "Vui lòng nhập lý do và mức độ vi phạm khi khóa tin");
        }
        Listing listing = getAnyListing(id);
        ListingStatus previous = listing.getStatus();
        ListingCategoryCountPublisher.Snapshot categoryCountBefore = categoryCountPublisher.snapshot(listing);
        ListingOwnerStatsPublisher.Snapshot ownerStatsBefore = ownerStatsPublisher.snapshot(listing);
        ListingStatus newStatus = listingStateMachine.transition(previous, ListingEvent.LOCK);

        String reason = HtmlSanitizer.stripAllHtml(request.getReason());
        ReportSeverity severity = request.getSeverity();
        listing.setStatus(newStatus);
        listing.setLockReason(reason);
        listing.setLockSeverity(severity);
        listingRepository.save(listing);
        categoryCountPublisher.publishIfChanged(categoryCountBefore, listing);
        ownerStatsPublisher.publishIfChanged(ownerStatsBefore, listing);

        Long modId = recordModeration(listing, actorId, ModerationActionType.LOCK,
                ModerationResult.SEVERE_LOCK, reason, previous, newStatus);
        Long auditId = auditLogService.recordChange(AuditAction.LISTING_LOCK, actorId, "LISTING",
                id, listing.getTitle(),
                previous.name(), newStatus.name() + " (" + severity.name() + ")", reason);

        boolean notified = false;
        if (!Boolean.FALSE.equals(request.getNotifyOwner())) {
            notificationService.notifyUser(listing.getOwnerId(), NotificationType.LISTING_LOCKED,
                    "Tin đăng của bạn đã bị khóa", "Lý do: " + reason);
            notified = true;
        }

        return AdminListingActionResponse.builder()
                .id(id).status(newStatus.name()).previousStatus(previous.name())
                .reason(reason).severity(severity.name())
                .moderatorId(actorId).moderationActionId(modId).auditLogId(auditId)
                .warningIssued(false).ownerNotified(notified).at(Instant.now()).build();
    }

    // ============================ Mở khóa ============================

    @Override
    @Transactional
    public AdminListingActionResponse unlock(Long id, UnlockListingRequest request, Long actorId) {
        Listing listing = getAnyListing(id);
        ListingStatus previous = listing.getStatus();
        ListingStatus newStatus = listingStateMachine.transition(previous, ListingEvent.UNLOCK);

        String reason = HtmlSanitizer.stripAllHtml(request.getReason());
        listing.setStatus(newStatus);
        listingRepository.save(listing);

        Long modId = recordModeration(listing, actorId, ModerationActionType.UNLOCK, null,
                reason, previous, newStatus);
        Long auditId = auditLogService.recordChange(AuditAction.LISTING_UNLOCK, actorId, "LISTING",
                id, listing.getTitle(), previous.name(), newStatus.name(), reason);

        notificationService.notifyUser(listing.getOwnerId(), NotificationType.LISTING_LOCKED,
                "Tin đăng của bạn đã được mở khóa",
                "Tin đang ở trạng thái ẩn, bạn cần tự bật lại sau khi chỉnh sửa.");

        return AdminListingActionResponse.builder()
                .id(id).status(newStatus.name()).previousStatus(previous.name())
                .reason(reason).moderatorId(actorId).moderationActionId(modId).auditLogId(auditId)
                .ownerNotified(true).at(Instant.now()).build();
    }

    // ============================ Ẩn (kiểm duyệt) ============================

    @Override
    @Transactional
    public AdminListingActionResponse hide(Long id, String reason, Long actorId) {
        Listing listing = getAnyListing(id);
        String cleaned = sanitize(reason, "Ẩn tin do vi phạm kiểm duyệt");

        // ACTIVE/NEED_REVIEW → HIDDEN qua gateway (ghi auto_hidden_at để chủ trọ không tự bật lại).
        ListingStatus previous = listingModerationGateway.hideByModeration(id, actorId, cleaned);
        ListingStatus newStatus = ListingStatus.HIDDEN;

        Long modId = recordModeration(listing, actorId, ModerationActionType.HIDE,
                ModerationResult.MEDIUM_HIDE, cleaned, previous, newStatus);
        Long auditId = auditLogService.recordChange(AuditAction.LISTING_HIDE, actorId, "LISTING",
                id, listing.getTitle(), previous.name(), newStatus.name(), cleaned);

        notificationService.notifyUser(listing.getOwnerId(), NotificationType.LISTING_HIDDEN,
                "Tin đăng của bạn đã bị ẩn", "Lý do: " + cleaned);

        return AdminListingActionResponse.builder()
                .id(id).status(newStatus.name()).previousStatus(previous.name())
                .reason(cleaned).moderatorId(actorId).moderationActionId(modId).auditLogId(auditId)
                .ownerNotified(true).at(Instant.now()).build();
    }

    // ============================ Bỏ ẩn ============================

    @Override
    @Transactional
    public AdminListingActionResponse unhide(Long id, String reason, Long actorId) {
        Listing listing = getAnyListing(id);
        ListingStatus previous = listing.getStatus();
        ListingCategoryCountPublisher.Snapshot categoryCountBefore = categoryCountPublisher.snapshot(listing);
        ListingOwnerStatsPublisher.Snapshot ownerStatsBefore = ownerStatsPublisher.snapshot(listing);
        // HIDDEN → ACTIVE qua máy trạng thái (ném LISTING_INVALID_TRANSITION nếu không ở HIDDEN).
        ListingStatus newStatus = listingStateMachine.transition(previous, ListingEvent.UNHIDE_BY_MODERATOR);

        String cleaned = sanitize(reason, "Khôi phục hiển thị tin sau kiểm duyệt");
        listing.setStatus(newStatus);
        // Xóa dấu ẩn tự động để tin trở lại hiển thị bình thường.
        listing.setAutoHiddenAt(null);
        listing.setAutoHideReason(null);
        listingRepository.save(listing);
        categoryCountPublisher.publishIfChanged(categoryCountBefore, listing);
        ownerStatsPublisher.publishIfChanged(ownerStatsBefore, listing);

        Long modId = recordModeration(listing, actorId, ModerationActionType.UNHIDE, null,
                cleaned, previous, newStatus);
        Long auditId = auditLogService.recordChange(AuditAction.LISTING_UNHIDE, actorId, "LISTING",
                id, listing.getTitle(), previous.name(), newStatus.name(), cleaned);

        notificationService.notifyUser(listing.getOwnerId(), NotificationType.LISTING_APPROVED,
                "Tin đăng của bạn đã được hiển thị lại",
                "Tin \"" + listing.getTitle() + "\" đã được kiểm duyệt viên bỏ ẩn và hiển thị công khai.");

        return AdminListingActionResponse.builder()
                .id(id).status(newStatus.name()).previousStatus(previous.name())
                .reason(cleaned).moderatorId(actorId).moderationActionId(modId).auditLogId(auditId)
                .ownerNotified(true).at(Instant.now()).build();
    }

    // ============================ Đánh dấu cần kiểm tra ============================

    @Override
    @Transactional
    public AdminListingActionResponse flagNeedReview(Long id, String reason, Long actorId) {
        Listing listing = getAnyListing(id);
        ListingStatus previous = listing.getStatus();
        // ACTIVE → NEED_REVIEW; validate qua máy trạng thái (ném lỗi rõ ràng nếu không hợp lệ).
        ListingStatus newStatus = listingStateMachine.transition(previous, ListingEvent.FLAG_NEED_REVIEW);

        String cleaned = sanitize(reason, "Đánh dấu cần kiểm tra");
        listingModerationGateway.flagNeedReview(id, cleaned);

        Long modId = recordModeration(listing, actorId, ModerationActionType.FLAG_NEED_REVIEW, null,
                cleaned, previous, newStatus);

        return AdminListingActionResponse.builder()
                .id(id).status(newStatus.name()).previousStatus(previous.name())
                .reason(cleaned).moderatorId(actorId).moderationActionId(modId)
                .ownerNotified(false).at(Instant.now()).build();
    }

    // ============================ Bỏ đánh dấu cần kiểm tra ============================

    @Override
    @Transactional
    public AdminListingActionResponse clearNeedReview(Long id, String reason, Long actorId) {
        Listing listing = getAnyListing(id);
        ListingStatus previous = listing.getStatus();
        // NEED_REVIEW → ACTIVE; validate qua máy trạng thái.
        ListingStatus newStatus = listingStateMachine.transition(previous, ListingEvent.CLEAR_NEED_REVIEW);

        String cleaned = sanitize(reason, "Bỏ đánh dấu cần kiểm tra (không vi phạm)");
        listingModerationGateway.clearNeedReview(id, actorId, cleaned);

        Long modId = recordModeration(listing, actorId, ModerationActionType.CLEAR_NEED_REVIEW, null,
                cleaned, previous, newStatus);

        return AdminListingActionResponse.builder()
                .id(id).status(newStatus.name()).previousStatus(previous.name())
                .reason(cleaned).moderatorId(actorId).moderationActionId(modId)
                .ownerNotified(false).at(Instant.now()).build();
    }

    // ============================ Yêu cầu chỉnh sửa ============================

    @Override
    @Transactional
    public AdminListingActionResponse requestEdit(Long id, String reason, Long actorId) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Vui lòng nhập nội dung yêu cầu chỉnh sửa");
        }
        Listing listing = getAnyListing(id);
        ListingStatus current = listing.getStatus();
        String cleaned = HtmlSanitizer.stripAllHtml(reason);

        // KHÔNG đổi trạng thái: state machine không có transition REQUEST_EDIT. Chỉ ghi nhật ký kiểm
        // duyệt + thông báo để chủ trọ tự chỉnh sửa (previous == newStatus).
        Long modId = recordModeration(listing, actorId, ModerationActionType.REQUEST_EDIT, null,
                cleaned, current, current);

        notificationService.notifyUser(listing.getOwnerId(), NotificationType.VIOLATION_WARNING,
                "Yêu cầu chỉnh sửa tin đăng",
                "Tin \"" + listing.getTitle() + "\" cần được chỉnh sửa. Nội dung: " + cleaned);

        return AdminListingActionResponse.builder()
                .id(id).status(current.name()).previousStatus(current.name())
                .reason(cleaned).moderatorId(actorId).moderationActionId(modId)
                .ownerNotified(true).at(Instant.now()).build();
    }

    // ============================ Thao tác hàng loạt ============================

    @Override
    public BulkActionResponse bulkModerate(BulkListingActionRequest request, Long actorId) {
        String action = request.getAction() != null ? request.getAction().trim().toUpperCase() : "";
        String reason = request.getReason();
        boolean reasonRequired = "REJECT".equals(action) || "LOCK".equals(action);
        if (reasonRequired && (reason == null || reason.isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Hành động " + action + " yêu cầu nhập lý do");
        }

        BulkActionResponse result = new BulkActionResponse();
        for (Long id : request.getIds()) {
            if (id == null) {
                continue;
            }
            try {
                // Gọi qua proxy (self) để mỗi tin chạy trong một giao dịch riêng — lỗi tin này không
                // cuốn theo tin khác.
                switch (action) {
                    case "APPROVE" -> self.approve(id, new ApproveListingRequest(), actorId);
                    case "REJECT" -> {
                        RejectListingRequest r = new RejectListingRequest();
                        r.setReason(reason);
                        self.reject(id, r, actorId);
                    }
                    case "LOCK" -> {
                        LockListingRequest r = new LockListingRequest();
                        r.setReason(reason);
                        r.setSeverity(ReportSeverity.MEDIUM);
                        self.lock(id, r, actorId);
                    }
                    case "HIDE" -> self.hide(id, reason, actorId);
                    default -> throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                            "Hành động không hợp lệ: " + action);
                }
                result.addSuccess(id);
            } catch (RuntimeException ex) {
                result.addFailure(id, ex.getMessage());
            }
        }
        return result.finish();
    }

    // ============================ Nội bộ ============================

    private Listing getAnyListing(Long id) {
        return listingRepository.findAnyById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LISTING_NOT_FOUND));
    }

    private Long recordModeration(Listing listing, Long actorId, ModerationActionType type,
                                  ModerationResult result, String reason,
                                  ListingStatus previous, ListingStatus newStatus) {
        ModerationAction action = ModerationAction.builder()
                .moderatorId(actorId)
                .isSystem(false)
                .targetType(ReportTargetType.LISTING)
                .targetId(listing.getId())
                .listingId(listing.getId())
                .actionType(type)
                .result(result)
                .reason(reason)
                .previousStatus(previous != null ? previous.name() : null)
                .newStatus(newStatus != null ? newStatus.name() : null)
                .build();
        return moderationActionRepository.save(action).getId();
    }

    private String sanitize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return HtmlSanitizer.stripAllHtml(value);
    }
}
