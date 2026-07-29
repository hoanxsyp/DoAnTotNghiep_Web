package com.webtro.modules.catalog.entity;

import com.webtro.common.BaseEntity;
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
 * Bảng tra cứu <b>tỉnh/thành phố</b> ({@code provinces}) — mục 13 của V1 baseline.
 *
 * <p>Cột {@code type} lưu VARCHAR với tập giá trị {@code THANH_PHO_TRUNG_UONG}/{@code TINH};
 * hiện chưa có enum tương ứng trong {@code com.webtro.common.enums} nên map dạng {@link String}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(
        name = "provinces",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_provinces_code", columnNames = "code"),
                @UniqueConstraint(name = "uk_provinces_slug", columnNames = "slug")
        },
        indexes = {
                @Index(name = "idx_provinces_is_active_display_order",
                        columnList = "is_active, display_order"),
                @Index(name = "idx_provinces_search_name", columnList = "search_name")
        }
)
public class Province extends BaseEntity {

    /** Mã tỉnh/thành. */
    @Column(name = "code", nullable = false, length = 10)
    private String code;

    /** Tên đầy đủ. */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Tên rút gọn. */
    @Column(name = "short_name", nullable = false, length = 50)
    private String shortName;

    /** Loại đơn vị: THANH_PHO_TRUNG_UONG hoặc TINH (VARCHAR, chưa có enum riêng). */
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

    /** Số tin đăng trong tỉnh/thành (đếm sẵn). */
    @Builder.Default
    @Column(name = "listing_count", nullable = false)
    private Integer listingCount = 0;
}
