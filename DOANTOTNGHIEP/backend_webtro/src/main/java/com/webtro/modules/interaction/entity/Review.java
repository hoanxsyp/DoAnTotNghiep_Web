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
 * <p>Mỗi người chỉ đánh giá một tin một lần ({@code uk_reviews_user_listing}). FK chéo module
 * ({@code listingId}, {@code userId}, {@code landlordId}, {@code hiddenBy}) giữ {@code Long}.
 * Theo doc 02, {@code rating} (TINYINT UNSIGNED) map sang {@code Integer}.
 */
@Entity
@Table(
        name = "reviews",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_reviews_user_listing",
                columnNames = {"user_id", "listing_id"}),
        indexes = {
                @Index(name = "idx_reviews_listing_id_status_created_at", columnList = "listing_id, status, created_at"),
                @Index(name = "idx_reviews_landlord_id_status", columnList = "landlord_id, status"),
                @Index(name = "idx_reviews_rating_status", columnList = "rating, status"),
                @Index(name = "idx_reviews_user_id", columnList = "user_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Review extends AuditableEntity {

    /** Tin được đánh giá (FK listings.id — chéo module, giữ Long). */
    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    /** Người đánh giá (FK users.id — chéo module, giữ Long). */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Chủ trọ được đánh giá (FK users.id — chéo module, giữ Long). */
    @Column(name = "landlord_id", nullable = false)
    private Long landlordId;

    /** Số sao 1–5 (TINYINT UNSIGNED → Integer theo doc 02). */
    @Column(name = "rating", nullable = false)
    private Integer rating;

    /** Nội dung — bắt buộc (≥3 ký tự) khi rating ≤ 2 (ck_reviews_content_required). */
    @Column(name = "content", length = 1000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 10)
    private ReviewStatus status = ReviewStatus.VISIBLE;

    /** Người đánh giá đã từng liên hệ tin (điều kiện {@code review.require_contact}). */
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

    /** Mốc chỉnh sửa gần nhất (trong cửa sổ {@code review.edit_window_hours}). */
    @Column(name = "edited_at")
    private Instant editedAt;
}
