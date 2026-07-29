package com.webtro.modules.interaction.specification;

import com.webtro.common.enums.ReviewStatus;
import com.webtro.modules.interaction.entity.Review;
import org.springframework.data.jpa.domain.Specification;

/**
 * Bộ {@link Specification} lọc động cho {@link Review} — phục vụ màn hình kiểm duyệt
 * ({@code GET /api/admin/reviews}). Luôn kết hợp với {@link #notDeleted()} để bỏ bản ghi đã xóa mềm.
 */
public final class ReviewSpecifications {

    private ReviewSpecifications() {
    }

    /** Chưa bị xóa mềm ({@code deleted_at IS NULL}). */
    public static Specification<Review> notDeleted() {
        return (root, q, cb) -> cb.isNull(root.get("deletedAt"));
    }

    /** Lọc theo trạng thái đánh giá. */
    public static Specification<Review> statusEq(ReviewStatus status) {
        return (root, q, cb) -> cb.equal(root.get("status"), status);
    }

    /** Lọc theo số sao. */
    public static Specification<Review> ratingEq(Integer rating) {
        return (root, q, cb) -> cb.equal(root.get("rating"), rating);
    }

    /** Lọc đánh giá của một tin. */
    public static Specification<Review> listingIdEq(Long listingId) {
        return (root, q, cb) -> cb.equal(root.get("listingId"), listingId);
    }

    /** Lọc đánh giá về một chủ trọ. */
    public static Specification<Review> landlordIdEq(Long landlordId) {
        return (root, q, cb) -> cb.equal(root.get("landlordId"), landlordId);
    }

    /** Tìm theo từ khóa trong nội dung (không phân biệt hoa thường). */
    public static Specification<Review> contentContains(String keyword) {
        String like = "%" + keyword.trim().toLowerCase() + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("content")), like);
    }
}
