package com.webtro.modules.catalog.entity;

import com.webtro.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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
 * Bảng tra cứu <b>quận/huyện</b> ({@code districts}) — mục 14 của V1 baseline.
 *
 * <p>Quan hệ trong cùng module catalog nên map {@code @ManyToOne} tới {@link Province} (LAZY).
 * Cột {@code type} lưu VARCHAR ({@code QUAN}/{@code HUYEN}/{@code THI_XA}/{@code THANH_PHO_THUOC_TINH}),
 * chưa có enum riêng nên map dạng {@link String}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(
        name = "districts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_districts_code", columnNames = "code"),
                @UniqueConstraint(name = "uk_districts_slug", columnNames = "slug")
        },
        indexes = {
                @Index(name = "idx_districts_province_id_is_active",
                        columnList = "province_id, is_active"),
                @Index(name = "idx_districts_search_name", columnList = "search_name")
        }
)
public class District extends BaseEntity {

    /** Tỉnh/thành cha. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "province_id", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_districts_provinces"))
    private Province province;

    /** Mã quận/huyện. */
    @Column(name = "code", nullable = false, length = 10)
    private String code;

    /** Tên đầy đủ. */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Loại đơn vị: QUAN/HUYEN/THI_XA/THANH_PHO_THUOC_TINH (VARCHAR, chưa có enum riêng). */
    @Column(name = "type", nullable = false, length = 20)
    private String type;

    /** Slug dùng cho URL. */
    @Column(name = "slug", nullable = false, length = 120)
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

    /** Thứ tự hiển thị. */
    @Builder.Default
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    /** Còn hoạt động hay không. */
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /** Số tin đăng trong quận/huyện (đếm sẵn). */
    @Builder.Default
    @Column(name = "listing_count", nullable = false)
    private Integer listingCount = 0;
}
