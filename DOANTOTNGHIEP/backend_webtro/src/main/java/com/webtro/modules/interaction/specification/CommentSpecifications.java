package com.webtro.modules.interaction.specification;

import com.webtro.common.enums.CommentStatus;
import com.webtro.common.enums.SentimentLabel;
import com.webtro.modules.interaction.entity.Comment;
import org.springframework.data.jpa.domain.Specification;

/**
 * Bộ {@link Specification} lọc động cho {@link Comment} — phục vụ màn hình kiểm duyệt
 * ({@code GET /api/admin/comments}). Luôn kết hợp với {@link #notDeleted()} để bỏ bản ghi đã xóa mềm.
 */
public final class CommentSpecifications {

    private CommentSpecifications() {
    }

    /** Chưa bị xóa mềm ({@code deleted_at IS NULL}). */
    public static Specification<Comment> notDeleted() {
        return (root, q, cb) -> cb.isNull(root.get("deletedAt"));
    }

    /** Lọc theo trạng thái bình luận. */
    public static Specification<Comment> statusEq(CommentStatus status) {
        return (root, q, cb) -> cb.equal(root.get("status"), status);
    }

    /** Lọc theo nhãn cảm xúc do AI gán. */
    public static Specification<Comment> sentimentEq(SentimentLabel sentiment) {
        return (root, q, cb) -> cb.equal(root.get("sentimentLabel"), sentiment);
    }

    /** Lọc theo cờ spam. */
    public static Specification<Comment> spamEq(Boolean isSpam) {
        return (root, q, cb) -> cb.equal(root.get("isSpam"), isSpam);
    }

    /** Lọc bình luận thuộc một tin. */
    public static Specification<Comment> listingIdEq(Long listingId) {
        return (root, q, cb) -> cb.equal(root.get("listingId"), listingId);
    }

    /** Tìm theo từ khóa trong nội dung (không phân biệt hoa thường). */
    public static Specification<Comment> contentContains(String keyword) {
        String like = "%" + keyword.trim().toLowerCase() + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("content")), like);
    }
}
