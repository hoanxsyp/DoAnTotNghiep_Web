package com.webtro.modules.catalog.entity;

import com.webtro.common.BaseEntity;
import com.webtro.common.enums.AmenityGroup;
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

/**
 * Bảng tra cứu <b>tiện ích</b> ({@code amenities}) — mục 16 của V1 baseline.
 *
 * <p>Cột {@code group_code} lưu enum {@link AmenityGroup} (VARCHAR). Cột {@code code} là chuỗi
 * tự do có unique key, chưa có enum riêng nên map dạng {@link String}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(
        name = "amenities",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_amenities_code", columnNames = "code")
        },
        indexes = {
                @Index(name = "idx_amenities_group_display_order",
                        columnList = "group_code, is_active, display_order")
        }
)
public class Amenity extends BaseEntity {

    /** Mã tiện ích (chuỗi tự do, unique). */
    @Column(name = "code", nullable = false, length = 30)
    private String code;

    /** Tên hiển thị. */
    @Column(name = "name", nullable = false, length = 60)
    private String name;

    /** Nhóm tiện ích (enum, lưu VARCHAR). */
    @Enumerated(EnumType.STRING)
    @Column(name = "group_code", nullable = false, length = 15)
    private AmenityGroup groupCode;

    /** Tên/định danh icon. */
    @Column(name = "icon", length = 50)
    private String icon;

    /** Có dùng để lọc tìm kiếm hay không. */
    @Builder.Default
    @Column(name = "is_filterable", nullable = false)
    private Boolean isFilterable = true;

    /** Hệ số ảnh hưởng tới giá (từ -1 đến 1). */
    @Builder.Default
    @Column(name = "price_impact_ratio", nullable = false, precision = 5, scale = 4)
    private BigDecimal priceImpactRatio = BigDecimal.ZERO;

    /** Thứ tự hiển thị. */
    @Builder.Default
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    /** Còn hoạt động hay không. */
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
