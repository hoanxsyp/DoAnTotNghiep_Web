package com.webtro.modules.ai.entity;

import com.webtro.common.enums.AdministrativeUnitType;
import com.webtro.common.enums.FurnitureStatus;
import com.webtro.common.enums.PriceConfidence;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lịch sử dự đoán giá ({@code prediction_histories}) — mục 41 của V1 baseline.
 *
 * <p><b>Append-only</b> (chỉ {@code id} + {@code created_at}): mỗi lần chạy ước lượng giá ghi một
 * dòng bất biến, phục vụ báo cáo và đánh giá chất lượng AI. Không có {@code updated_at}/xóa mềm nên
 * <b>không kế thừa</b> base class; khai {@code id}/{@code created_at} tại chỗ.
 *
 * <p>{@code listingId} (nullable — dự đoán có thể có trước khi tạo tin), {@code userId},
 * {@code categoryId}, {@code provinceId}, {@code districtId}, {@code wardId} đều trỏ sang module
 * khác nên giữ {@code Long}, không map quan hệ (canonical luật 8).
 *
 * <p>Tồn tại vòng FK {@code listings.price_prediction_id -> prediction_histories.id} và
 * {@code prediction_histories.listing_id -> listings.id}; cả hai nullable, giải quyết ở tầng DB.
 */
@Entity
@Table(
        name = "prediction_histories",
        indexes = {
                @Index(name = "idx_prediction_histories_listing_id_created_at", columnList = "listing_id, created_at"),
                @Index(name = "idx_prediction_histories_user_id_created_at", columnList = "user_id, created_at"),
                @Index(name = "idx_prediction_histories_is_flagged", columnList = "is_flagged, created_at"),
                @Index(name = "idx_prediction_histories_confidence", columnList = "confidence"),
                @Index(name = "idx_prediction_histories_category_id", columnList = "category_id"),
                @Index(name = "idx_prediction_histories_province_id", columnList = "province_id"),
                @Index(name = "idx_prediction_histories_district_id", columnList = "district_id"),
                @Index(name = "idx_prediction_histories_ward_id", columnList = "ward_id"),
                @Index(name = "idx_prediction_histories_sample_size", columnList = "sample_size")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PredictionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /** Tin gắn dự đoán — {@code null} nếu dự đoán từ form chưa thành tin (FK listings.id, chéo module). */
    @Column(name = "listing_id")
    private Long listingId;

    /** Người yêu cầu dự đoán (FK users.id — chéo module, giữ Long). */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Loại hình tin (FK categories.id — chéo module, giữ Long). */
    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    /** Tỉnh/thành (FK provinces.id — chéo module, giữ Long). */
    @Column(name = "province_id", nullable = false)
    private Long provinceId;

    /** Quận/huyện (FK districts.id — chéo module, giữ Long). */
    @Column(name = "district_id", nullable = false)
    private Long districtId;

    /** Phường/xã (FK wards.id — chéo module, giữ Long). */
    @Column(name = "ward_id", nullable = false)
    private Long wardId;

    /** Diện tích đầu vào (> 0). */
    @Column(name = "area", nullable = false, precision = 8, scale = 2)
    private BigDecimal area;

    /** Số phòng đầu vào. */
    @Column(name = "room_count")
    private Integer roomCount;

    /** Số nhà vệ sinh đầu vào. */
    @Column(name = "toilet_count")
    private Integer toiletCount;

    /** Tình trạng nội thất đầu vào. */
    @Enumerated(EnumType.STRING)
    @Column(name = "furniture_status", length = 10)
    private FurnitureStatus furnitureStatus;

    /** Danh sách id tiện ích đầu vào (JSON thô). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "amenity_ids", columnDefinition = "json")
    private String amenityIds;

    /** Giá đề xuất; {@code null} khi confidence = INSUFFICIENT_DATA. */
    @Column(name = "suggested_price", precision = 15, scale = 2)
    private BigDecimal suggestedPrice;

    /** Cận dưới khoảng giá. */
    @Column(name = "price_low", precision = 15, scale = 2)
    private BigDecimal priceLow;

    /** Giá trung vị. */
    @Column(name = "price_median", precision = 15, scale = 2)
    private BigDecimal priceMedian;

    /** Cận trên khoảng giá. */
    @Column(name = "price_high", precision = 15, scale = 2)
    private BigDecimal priceHigh;

    /** Giá trên mỗi m². */
    @Column(name = "price_per_sqm", precision = 15, scale = 2)
    private BigDecimal pricePerSqm;

    /** Số mẫu dữ liệu dùng để ước lượng. */
    @Builder.Default
    @Column(name = "sample_size", nullable = false)
    private Integer sampleSize = 0;

    /** Phạm vi địa lý thực dùng khi đủ mẫu (WARD/DISTRICT/PROVINCE). */
    @Enumerated(EnumType.STRING)
    @Column(name = "scope_used", nullable = false, length = 10)
    private AdministrativeUnitType scopeUsed;

    /** Mức tin cậy của dự đoán. */
    @Enumerated(EnumType.STRING)
    @Column(name = "confidence", nullable = false, length = 20)
    private PriceConfidence confidence;

    /** Tỷ lệ phân tán giá của mẫu. */
    @Column(name = "dispersion_ratio", precision = 6, scale = 4)
    private BigDecimal dispersionRatio;

    /** Chi tiết hệ số điều chỉnh theo tiện ích/nội thất (JSON thô). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "adjustment_detail", columnDefinition = "json")
    private String adjustmentDetail;

    /** Giải thích dự đoán (hiển thị cho người dùng). */
    @Column(name = "explanation", length = 500)
    private String explanation;

    /** Giá người dùng nhập (để so lệch với dự đoán). */
    @Column(name = "input_price", precision = 15, scale = 2)
    private BigDecimal inputPrice;

    /** Tỷ lệ lệch giữa giá nhập và dự đoán. */
    @Column(name = "deviation_ratio", precision = 6, scale = 4)
    private BigDecimal deviationRatio;

    /** Bị đánh cờ lệch giá (phục vụ danh sách tin nghi ngờ). */
    @Builder.Default
    @Column(name = "is_flagged", nullable = false)
    private Boolean isFlagged = false;

    /** Người dùng có áp dụng giá đề xuất không. */
    @Builder.Default
    @Column(name = "is_applied", nullable = false)
    private Boolean isApplied = false;

    /** Phiên bản bộ ước lượng (đối chiếu ai_configs.version). */
    @Column(name = "estimator_version", nullable = false, length = 20)
    private String estimatorVersion;

    /** Thời điểm ghi nhận (append-only). */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
