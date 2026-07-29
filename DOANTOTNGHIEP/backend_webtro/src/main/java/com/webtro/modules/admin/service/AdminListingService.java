package com.webtro.modules.admin.service;

import com.webtro.common.BulkActionResponse;
import com.webtro.common.PageResponse;
import com.webtro.common.enums.ListingStatus;
import com.webtro.modules.admin.dto.request.ApproveListingRequest;
import com.webtro.modules.admin.dto.request.BulkListingActionRequest;
import com.webtro.modules.admin.dto.request.LockListingRequest;
import com.webtro.modules.admin.dto.request.RejectListingRequest;
import com.webtro.modules.admin.dto.request.UnlockListingRequest;
import com.webtro.modules.admin.dto.response.AdminListingActionResponse;
import com.webtro.modules.admin.dto.response.AdminListingResponse;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

/**
 * Kiểm duyệt tin đăng ở góc nhìn Admin/Moderator (canonical 4.14). Mọi chuyển trạng thái đi qua
 * {@code ListingStateMachine}; mọi thao tác ghi {@code moderation_actions} + {@code audit_logs} và
 * thông báo chủ trọ.
 */
public interface AdminListingService {

    /** Danh sách tin đăng có lọc (canonical 4.14.1) — cần {@code LISTING_VIEW_ANY}. */
    PageResponse<AdminListingResponse> searchListings(List<ListingStatus> statuses, String keyword,
                                                      Long ownerId, Long categoryId, Long provinceId,
                                                      Long districtId, Long wardId,
                                                      Boolean priceDeviationFlagged,
                                                      Instant from, Instant to, Pageable pageable);

    /**
     * Hàng đợi kiểm duyệt: tin đang chờ xử lý ({@code PENDING} + {@code NEED_REVIEW}), ưu tiên tin cũ
     * trước (canonical 4.14) — cần {@code LISTING_VIEW_ANY}.
     */
    PageResponse<AdminListingResponse> moderationQueue(Pageable pageable);

    /** Chi tiết một tin cho Admin/Moderator, gồm cả tin không công khai — cần {@code LISTING_VIEW_ANY}. */
    AdminListingResponse getListingDetail(Long id);

    /** Ẩn tin do kiểm duyệt: {@code ACTIVE/NEED_REVIEW → HIDDEN} — cần {@code LISTING_MODERATE}. */
    AdminListingActionResponse hide(Long id, String reason, Long actorId);

    /** Bỏ ẩn tin: {@code HIDDEN → ACTIVE} — cần {@code LISTING_MODERATE}. */
    AdminListingActionResponse unhide(Long id, String reason, Long actorId);

    /** Đánh dấu tin cần kiểm tra: {@code ACTIVE → NEED_REVIEW} — cần {@code LISTING_MODERATE}. */
    AdminListingActionResponse flagNeedReview(Long id, String reason, Long actorId);

    /** Bỏ đánh dấu cần kiểm tra: {@code NEED_REVIEW → ACTIVE} — cần {@code LISTING_MODERATE}. */
    AdminListingActionResponse clearNeedReview(Long id, String reason, Long actorId);

    /** Duyệt tin: PENDING → ACTIVE (canonical 4.14.3) — cần {@code LISTING_MODERATE}. */
    AdminListingActionResponse approve(Long id, ApproveListingRequest request, Long actorId);

    /** Từ chối tin: PENDING → REJECTED, bắt buộc lý do (canonical 4.14.4) — cần {@code LISTING_MODERATE}. */
    AdminListingActionResponse reject(Long id, RejectListingRequest request, Long actorId);

    /** Khóa tin → LOCKED, bắt buộc lý do + severity (canonical 4.14.5) — cần {@code LISTING_LOCK}. */
    AdminListingActionResponse lock(Long id, LockListingRequest request, Long actorId);

    /** Mở khóa tin: LOCKED → HIDDEN (canonical 4.14.6) — cần {@code LISTING_LOCK}. */
    AdminListingActionResponse unlock(Long id, UnlockListingRequest request, Long actorId);

    /**
     * Yêu cầu chủ trọ chỉnh sửa tin — cần {@code LISTING_MODERATE}. KHÔNG đổi trạng thái (state
     * machine không có transition tương ứng): chỉ ghi hành động {@code REQUEST_EDIT} + thông báo.
     */
    AdminListingActionResponse requestEdit(Long id, String reason, Long actorId);

    /**
     * Thao tác kiểm duyệt hàng loạt (APPROVE/REJECT/LOCK/HIDE) — cần {@code LISTING_MODERATE}. Mỗi
     * tin xử lý trong một giao dịch riêng; trả về danh sách id thành công + id thất bại kèm lý do.
     */
    BulkActionResponse bulkModerate(BulkListingActionRequest request, Long actorId);
}
