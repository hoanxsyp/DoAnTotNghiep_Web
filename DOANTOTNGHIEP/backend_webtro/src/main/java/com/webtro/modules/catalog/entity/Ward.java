package com.webtro.modules.catalog.entity;

import com.webtro.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
 * Bảng tra cứu <b>phường/xã</b> ({@code wards}) — mục 15 của V1 baseline.
 *
 * <p>Quan hệ trong cùng module catalog nên map {@code @ManyToOne} tới {@link District} (LAZY).
 * Cột {@code type} lưu VARCHAR ({@code PHUONG}/{@code XA}/{@code THI_TRAN}), chưa có enum riêng
 * nên map dạng {@link String}. Lưu ý: bảng này KHÔNG có cột {@code display_order}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(
        name = "wards",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_wards_code", columnNames = "code"),
                @UniqueConstraint(name = "uk_wards_slug", columnNames = "slug")
        },
        indexes = {
                @Index(name = "idx_wards_district_id_is_active",
                        columnList = "district_id, is_active"),
                @Index(name = "idx_wards_search_name", columnList = "search_name")
        }
)
public class Ward extends BaseEntity {

    /** Quận/huyện cha. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "district_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_wards_districts"))
    private District district;

    /** Mã phường/xã. */
    @Column(name = "code", nullable = false, length = 10)
    private String code;

    /** Tên đầy đủ. */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Loại đơn vị: PHUONG/XA/THI_TRAN (VARCHAR, chưa có enum riêng). */
    @Column(name = "type", nullable = false, length = 20)
    private String type;

    /** Slug dùng cho URL. */
    @Column(name = "slug", nullable = false, length = 160)
    private String slug;

    /** Tên không dấu phục vụ tìm kiếm. */
    @Column(name = "search_name", nullable = false, length = 100)
    private String searchName;

    /** Vĩ độ trung tâm. */
    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    /** Kinh độ trung tâm. */
    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    /** Còn hoạt động hay không. */
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /** Số tin đăng trong phường/xã (đếm sẵn). */
    @Builder.Default
    @Column(name = "listing_count", nullable = false)
    private Integer listingCount = 0;
}
