package com.webtro.modules.moderation.spi;

import java.util.Optional;

/**
 * Cổng (SPI) mà module {@code moderation} cần từ module {@code interaction} để kiểm tra và ẩn
 * bình luận / đánh giá bị báo cáo — canonical luật 4, {@code [§2.8]} RPT-02/03.
 *
 * <p>Xem giải thích quyết định kiến trúc ở {@link ListingModerationGateway}. Module
 * {@code interaction} phải cung cấp một {@code @Component} hiện thực interface này (bọc
 * {@code CommentRepository} + {@code ReviewRepository}). Việc ẩn nội dung đi qua service nội bộ
 * của interaction để giữ đúng ràng buộc {@code hidden_reason}/{@code hidden_by}/{@code hidden_at}.
 */
public interface ContentModerationGateway {

    /**
     * Tóm tắt một mẩu nội dung (bình luận hoặc đánh giá) bị báo cáo.
     *
     * @param id        id nội dung
     * @param authorId  người tạo nội dung (users.id) — dùng để chặn tự báo cáo và để gửi cảnh báo
     * @param listingId tin chứa nội dung (nếu có)
     * @param excerpt   trích nội dung (đã cắt) để hiển thị cho Moderator
     */
    record ContentRef(Long id, Long authorId, Long listingId, String excerpt) {
    }

    /** Tóm tắt bình luận còn sống nếu tồn tại. */
    Optional<ContentRef> findComment(Long commentId);

    /** Tóm tắt đánh giá còn sống nếu tồn tại. */
    Optional<ContentRef> findReview(Long reviewId);

    /** Ẩn một bình luận do kiểm duyệt ({@code CommentStatus = HIDDEN}, ghi lý do/người/thời điểm ẩn). */
    void hideComment(Long commentId, Long moderatorId, String reason);

    /** Ẩn một đánh giá do kiểm duyệt ({@code ReviewStatus = HIDDEN}). */
    void hideReview(Long reviewId, Long moderatorId, String reason);
}
