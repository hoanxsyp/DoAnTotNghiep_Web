package com.webtro.modules.payment.entity;

import com.webtro.common.AuditableEntity;
import com.webtro.common.enums.CouponDiscountType;
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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Mã giảm giá ({@code coupons}) — mục 34 của V1 baseline.
 *
 * <p>Bảng nghiệp vụ (có xóa mềm) nên kế thừa {@link AuditableEntity}. Áp dụng cho thanh toán mua
 * gói đẩy tin; giới hạn tổng lượt dùng ({@code usage_limit}) và lượt dùng mỗi người
 * ({@code per_user_limit}) trong cửa sổ hiệu lực [{@code start_at}, {@code end_at}].
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(
        name = "coupons",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_coupons_code", columnNames = "code")
        },
        indexes = {
                @Index(name = "idx_coupons_active_window", columnList = "is_active, start_at, end_at")
        }
)
public class Coupon extends AuditableEntity {

    /** Mã coupon (duy nhất). */
    @Column(name = "code", nullable = false, length = 30)
    private String code;

    /** Mô tả. */
    @Column(name = "description", length = 255)
    private String description;

    /** Loại giảm giá (PERCENT/FIXED), lưu VARCHAR. */
    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 10)
    private CouponDiscountType discountType;

    /** Giá trị giảm: phần trăm (<=100) nếu PERCENT, hoặc số tiền nếu FIXED. */
    @Column(name = "discount_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountValue;

    /** Mức giảm tối đa (chỉ áp dụng cho PERCENT); null = không giới hạn. */
    @Column(name = "max_discount_amount", precision = 15, scale = 2)
    private BigDecimal maxDiscountAmount;

    /** Giá trị đơn tối thiểu để được áp mã. */
    @Builder.Default
    @Column(name = "min_order_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal minOrderAmount = BigDecimal.ZERO;

    /** Tổng số lượt dùng tối đa; null = không giới hạn. */
    @Column(name = "usage_limit")
    private Integer usageLimit;

    /** Số lượt đã dùng (đếm sẵn). */
    @Builder.Default
    @Column(name = "used_count", nullable = false)
    private Integer usedCount = 0;

    /** Số lượt tối đa mỗi người dùng. */
    @Builder.Default
    @Column(name = "per_user_limit", nullable = false)
    private Integer perUserLimit = 1;

    /** Thời điểm bắt đầu hiệu lực (UTC). */
    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    /** Thời điểm kết thúc hiệu lực (UTC). */
    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    /** Còn hoạt động hay không. */
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
