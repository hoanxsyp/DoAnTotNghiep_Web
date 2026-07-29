package com.webtro.modules.payment.entity;

import com.webtro.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * Gói dịch vụ đẩy tin/khuyến mãi ({@code promotion_packages}) — mục 33 của V1 baseline.
 *
 * <p>Bảng nghiệp vụ (có xóa mềm) nên kế thừa {@link AuditableEntity}. Người dùng mua gói này để
 * đẩy tin đăng lên vị trí ưu tiên trong thời gian {@code duration_days} ngày.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(
        name = "promotion_packages",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_promotion_packages_code", columnNames = "code")
        },
        indexes = {
                @Index(name = "idx_promotion_packages_is_active_display_order",
                        columnList = "is_active, display_order"),
                @Index(name = "idx_promotion_packages_priority", columnList = "priority")
        }
)
public class PromotionPackage extends AuditableEntity {

    /** Mã gói (duy nhất). */
    @Column(name = "code", nullable = false, length = 30)
    private String code;

    /** Tên hiển thị của gói. */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Mô tả gói. */
    @Column(name = "description", length = 500)
    private String description;

    /** Giá gói (VND). */
    @Column(name = "price", nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    /** Số ngày hiệu lực của gói. */
    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    /** Mức ưu tiên hiển thị khi tin được đẩy (0..100). */
    @Builder.Default
    @Column(name = "priority", nullable = false)
    private Integer priority = 0;

    /** Nhãn huy hiệu hiển thị trên tin (vd "VIP", "HOT"). */
    @Column(name = "badge_label", length = 30)
    private String badgeLabel;

    /** Màu huy hiệu. */
    @Column(name = "badge_color", length = 20)
    private String badgeColor;

    /** Còn hoạt động (được bán) hay không. */
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /** Thứ tự hiển thị trong danh sách gói. */
    @Builder.Default
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    /** Số lượt đã mua (đếm sẵn). */
    @Builder.Default
    @Column(name = "purchase_count", nullable = false)
    private Integer purchaseCount = 0;
}
