package com.webtro.modules.interaction.entity;

import com.webtro.common.AuditableEntity;
import com.webtro.common.enums.ReviewStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Đánh giá (sao + nội dung) của người thuê cho tin/chủ trọ — bảng {@code reviews}.
 *
 * <p>Đánh giá gắn vào <b>Phòng</b> ({@code rentalUnitId}), không gắn vào tin: đóng tin rồi đăng
 * lại cùng phòng thì đánh giá vẫn còn (RE-1, RE-2). Mỗi người chỉ có một đánh giá chưa xóa mềm
 * cho một phòng. {@code activeMarker = null} với bản đã xóa giúp giải phóng khóa duy nhất để người
 * dùng có thể đánh giá lại. FK chéo module ({@code listingId}, {@code rentalUnitId},
 * {@code userId}, {@code hiddenBy}) giữ {@code Long}; chủ trọ suy ra từ phòng.
 * Theo doc 02, {@code rating} (TINYINT UNSIGNED) map sang {@code Integer}.
 */
@Entity
@Table(
        name = "reviews",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_reviews_active_user_unit",
                columnNames = {"user_id", "rental_unit_id", "active_marker"}),
        indexes = {
                @Index(name = "idx_reviews_unit_status_created", columnList = "rental_unit_id, status, created_at"),
                @Index(name = "idx_reviews_listing_id_status_created_at", columnList = "listing_id, status, created_at"),
                @Index(name = "idx_reviews_rating_status", columnList = "rating, status"),
                @Index(name = "idx_reviews_user_id", columnList = "user_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Review extends AuditableEntity {

    /** Phòng được đánh giá (FK rental_units.id — chéo module, giữ Long). */
    @Column(name = "rental_unit_id", nullable = false)
    private Long rentalUnitId;

    /**
     * Tin mà người đánh giá đã thuê qua (FK listings.id, {@code ON DELETE SET NULL}). Dùng để hiện
     * ghi chú "thuê qua tin tháng MM/YYYY"; có thể {@code null} nếu tin bị xoá cứng.
     */
    @Column(name = "listing_id")
    private Long listingId;

    /** Người đánh giá (FK users.id — chéo module, giữ Long). */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Chủ trọ được đánh giá (FK users.id — chéo module, giữ Long). */

    /** Số sao 1–5 (TINYINT UNSIGNED → Integer theo doc 02). */
    @Column(name = "rating", nullable = false)
    private Integer rating;

    /** Nội dung — bắt buộc (≥3 ký tự) khi rating ≤ 2 (ck_reviews_content_required). */
    @Column(name = "content", length = 2000)
    private String content;

    /** 1 với đánh giá còn hiệu lực; null sau khi xóa mềm để cho phép đánh giá lại cùng tin. */
    @Builder.Default
    @Column(name = "active_marker")
    private Byte activeMarker = 1;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 10)
    private ReviewStatus status = ReviewStatus.VISIBLE;

    /** Cột legacy; true khi quan hệ thuê đã được xác thực lúc tạo đánh giá. */
    @Builder.Default
    @Column(name = "is_verified_contact", nullable = false)
    private Boolean isVerifiedContact = false;

    /** Lý do ẩn — bắt buộc khi {@code status = HIDDEN} (ck_reviews_hidden_reason). */
    @Column(name = "hidden_reason", length = 500)
    private String hiddenReason;

    /** Người ẩn (FK users.id — chéo module, giữ Long). */
    @Column(name = "hidden_by")
    private Long hiddenBy;

    @Column(name = "hidden_at")
    private Instant hiddenAt;

    /** Mốc chỉnh sửa gần nhất. */
    @Column(name = "edited_at")
    private Instant editedAt;
}
