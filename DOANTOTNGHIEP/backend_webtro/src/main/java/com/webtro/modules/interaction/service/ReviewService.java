package com.webtro.modules.interaction.service;

import com.webtro.common.PageResponse;
import com.webtro.common.enums.ReviewStatus;
import com.webtro.modules.interaction.dto.request.CreateReviewRequest;
import com.webtro.modules.interaction.dto.response.AdminReviewResponse;
import com.webtro.modules.interaction.dto.response.ReviewEligibilityResponse;
import com.webtro.modules.interaction.dto.response.ReviewPageResponse;
import com.webtro.modules.interaction.dto.response.ReviewResponse;
import org.springframework.data.domain.Pageable;

/**
 * Nghiệp vụ đánh giá — REV, canonical mục 4.7, {@code [§3.12]}.
 */
public interface ReviewService {

    /** Danh sách đánh giá hiển thị của tin (ẩn danh) + thống kê trung bình/phân bố sao. */
    ReviewPageResponse listListingReviews(Long listingId, Integer rating, Pageable pageable);

    /** Danh sách đánh giá về một chủ trọ (tổng hợp trên mọi tin). */
    ReviewPageResponse listLandlordReviews(Long landlordId, Integer rating, Pageable pageable);

    /** Kiểm tra điều kiện đánh giá (luôn 200). */
    ReviewEligibilityResponse checkEligibility(Long listingId, Long userId);

    /** Tạo đánh giá; cập nhật trung bình của tin + chủ trọ; kích hoạt tính lại uy tín. */
    ReviewResponse createReview(Long listingId, CreateReviewRequest request, Long userId);

    /** Sửa đánh giá trong cửa sổ {@code review.edit_window_hours} (chỉ tác giả). */
    ReviewResponse updateReview(Long reviewId, CreateReviewRequest request, Long userId);

    /** Xóa (tác giả) hoặc ẩn (REVIEW_MODERATE, kèm lý do) đánh giá; cập nhật lại trung bình. */
    void deleteReview(Long reviewId, Long userId, String moderationReason);

    /** Đánh giá của chính người dùng (gồm cả bản bị ẩn, kèm lý do). */
    PageResponse<ReviewResponse> listMyReviews(Long userId, Pageable pageable);

    /**
     * Ẩn đánh giá do kiểm duyệt ({@code status = HIDDEN}, ghi {@code hidden_reason/by/at}); tính lại
     * trung bình sao của tin/chủ trọ + điểm uy tín. Gọi bởi adapter {@code ContentModerationGateway}
     * của module moderation.
     */
    void hideByModeration(Long reviewId, Long moderatorId, String reason);

    // ------------------ Kiểm duyệt (REVIEW_MODERATE) — canonical 4.7.10 ------------------

    /**
     * Danh sách đánh giá cho màn hình kiểm duyệt: lọc theo trạng thái / số sao / tin / chủ trọ /
     * từ khóa nội dung, phân trang; kèm thông tin ẩn.
     */
    PageResponse<AdminReviewResponse> adminListReviews(ReviewStatus status, Integer rating, Long listingId,
                                                       Long landlordId, String keyword, Pageable pageable);

    /**
     * Ẩn đánh giá vi phạm (dùng cho endpoint admin): đặt {@code HIDDEN}, ghi lý do/người/thời điểm,
     * tính lại trung bình sao + uy tín và ghi AuditLog {@code REVIEW_HIDE}. Trả bản ghi sau khi ẩn.
     */
    AdminReviewResponse adminHideReview(Long reviewId, Long moderatorId, String reason);

    /**
     * Khôi phục đánh giá đã ẩn về {@code VISIBLE} (xóa {@code hidden_reason/by/at}); cộng lại vào
     * trung bình sao + uy tín và ghi AuditLog {@code REVIEW_UNHIDE}. Trả bản ghi sau khi khôi phục.
     */
    AdminReviewResponse unhideByModeration(Long reviewId, Long moderatorId);
}
