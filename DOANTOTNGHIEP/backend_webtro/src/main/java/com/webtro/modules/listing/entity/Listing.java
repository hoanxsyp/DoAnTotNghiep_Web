package com.webtro.modules.listing.entity;

import com.webtro.common.AuditableEntity;
import com.webtro.common.enums.CurfewType;
import com.webtro.common.enums.FurnitureStatus;
import com.webtro.common.enums.GenderRequirement;
import com.webtro.common.enums.ListingStatus;
import com.webtro.common.enums.ReportSeverity;
import com.webtro.common.enums.ToiletType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Tin đăng phòng trọ [§3.3][§5.1]. Bảng nghiệp vụ (có xóa mềm, audit).
 *
 * <p>Vòng đời trạng thái điều khiển bởi ListingStateMachine; điều kiện
 * {@code deleted_at IS NULL} luôn viết tường minh trong repository (không dùng
 * {@code @SQLRestriction}). Các khóa ngoại trỏ sang module khác (owner, category,
 * province/district/ward, price_prediction) chỉ giữ dạng {@code Long} theo canonical luật 8.
 */
@Entity
@Table(
        name = "listings",
        uniqueConstraints = @UniqueConstraint(name = "uk_listings_slug", columnNames = "slug")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Listing extends AuditableEntity {

    // ----- Khóa ngoại chéo module (canonical luật 8: chỉ giữ id, không map object) -----

    /** Chủ tin (users.id). */
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    /** Danh mục (categories.id). */
    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    // ----- Thông tin cơ bản -----

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "slug", nullable = false, length = 180)
    private String slug;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "price", nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @Column(name = "area", nullable = false, precision = 8, scale = 2)
    private BigDecimal area;

    @Column(name = "deposit_amount", precision = 15, scale = 2)
    private BigDecimal depositAmount;

    @Column(name = "electricity_price", precision = 15, scale = 2)
    private BigDecimal electricityPrice;

    @Column(name = "water_price", precision = 15, scale = 2)
    private BigDecimal waterPrice;

    // ----- Địa chỉ -----

    /** Tỉnh/thành (provinces.id). */

    /** Quận/huyện (districts.id). */

    /** Phường/xã (wards.id). */
    @Column(name = "ward_id", nullable = false)
    private Long wardId;

    @Column(name = "address_detail", nullable = false, length = 255)
    private String addressDetail;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    // ----- Đặc điểm phòng -----

    @Column(name = "room_count")
    private Integer roomCount;

    @Column(name = "toilet_count")
    private Integer toiletCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "toilet_type", length = 10)
    private ToiletType toiletType;

    @Column(name = "max_occupants")
    private Integer maxOccupants;

    @Column(name = "current_occupants")
    private Integer currentOccupants;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender_requirement", nullable = false, length = 15)
    @lombok.Builder.Default
    private GenderRequirement genderRequirement = GenderRequirement.ANY;

    @Column(name = "pet_allowed", nullable = false)
    @lombok.Builder.Default
    private Boolean petAllowed = false;

    @Column(name = "parking_available", nullable = false)
    @lombok.Builder.Default
    private Boolean parkingAvailable = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "curfew_type", nullable = false, length = 10)
    @lombok.Builder.Default
    private CurfewType curfewType = CurfewType.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(name = "furniture_status", nullable = false, length = 10)
    @lombok.Builder.Default
    private FurnitureStatus furnitureStatus = FurnitureStatus.NONE;

    @Column(name = "available_from")
    private LocalDate availableFrom;

    // ----- Trạng thái & kiểm duyệt -----

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @lombok.Builder.Default
    private ListingStatus status = ListingStatus.DRAFT;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Column(name = "lock_reason", length = 500)
    private String lockReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "lock_severity", length = 10)
    private ReportSeverity lockSeverity;

    @Column(name = "auto_hidden_at")
    private Instant autoHiddenAt;

    /** Lý do tự ẩn (văn bản mô tả, không phải mã enum — cột dài 500). */
    @Column(name = "auto_hide_reason", length = 500)
    private String autoHideReason;

    // ----- Điểm số & bộ đếm (denormalized, job/transaction cập nhật) -----

    @Column(name = "trust_score", nullable = false, precision = 5, scale = 2)
    @lombok.Builder.Default
    private BigDecimal trustScore = new BigDecimal("100.00");

    @Column(name = "average_rating", nullable = false, precision = 3, scale = 2)
    @lombok.Builder.Default
    private BigDecimal averageRating = new BigDecimal("0.00");

    @Column(name = "review_count", nullable = false)
    @lombok.Builder.Default
    private Integer reviewCount = 0;

    @Column(name = "view_count", nullable = false)
    @lombok.Builder.Default
    private Integer viewCount = 0;

    @Column(name = "favorite_count", nullable = false)
    @lombok.Builder.Default
    private Integer favoriteCount = 0;

    @Column(name = "contact_count", nullable = false)
    @lombok.Builder.Default
    private Integer contactCount = 0;

    @Column(name = "comment_count", nullable = false)
    @lombok.Builder.Default
    private Integer commentCount = 0;

    @Column(name = "negative_comment_count", nullable = false)
    @lombok.Builder.Default
    private Integer negativeCommentCount = 0;

    @Column(name = "positive_comment_count", nullable = false)
    @lombok.Builder.Default
    private Integer positiveCommentCount = 0;

    @Column(name = "need_review_count", nullable = false)
    @lombok.Builder.Default
    private Integer needReviewCount = 0;

    @Column(name = "last_need_review_at")
    private Instant lastNeedReviewAt;

    // ----- Dự đoán giá (AI module) -----

    /** prediction_histories.id (chéo module → chỉ giữ Long). */
    @Column(name = "price_prediction_id")
    private Long pricePredictionId;

    @Column(name = "price_deviation_flag", nullable = false)
    @lombok.Builder.Default
    private Boolean priceDeviationFlag = false;

    // ----- Đẩy tin / promotion -----

    @Column(name = "is_promoted", nullable = false)
    @lombok.Builder.Default
    private Boolean isPromoted = false;

    @Column(name = "promoted_until")
    private Instant promotedUntil;

    @Column(name = "promotion_priority", nullable = false)
    @lombok.Builder.Default
    private Integer promotionPriority = 0;

    // ----- Vòng đời xuất bản -----

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @Column(name = "expiry_reminder_sent_at")
    private Instant expiryReminderSentAt;

    @Column(name = "renew_count", nullable = false)
    @lombok.Builder.Default
    private Integer renewCount = 0;

    @Column(name = "floor_count")
    private Integer floorCount;
}
