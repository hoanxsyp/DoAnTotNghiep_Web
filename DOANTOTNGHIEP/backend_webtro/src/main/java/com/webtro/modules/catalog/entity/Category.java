package com.webtro.modules.catalog.entity;

import com.webtro.common.BaseEntity;
import com.webtro.common.enums.CategoryCode;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Bảng tra cứu <b>loại tin đăng</b> ({@code categories}) — mục 12 của V1 baseline.
 *
 * <p>Bảng tra cứu nên kế thừa {@link BaseEntity} (chỉ id/created_at/updated_at, không xóa mềm).
 * Hai cột {@code required_fields}/{@code optional_fields} là JSON, lưu chuỗi JSON thô bằng hỗ trợ
 * JSON gốc của Hibernate 6 ({@code @JdbcTypeCode(SqlTypes.JSON)}).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(
        name = "categories",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_categories_code", columnNames = "code"),
                @UniqueConstraint(name = "uk_categories_slug", columnNames = "slug")
        },
        indexes = {
                @Index(name = "idx_categories_is_active_display_order",
                        columnList = "is_active, display_order")
        }
)
public class Category extends BaseEntity {

    /** Mã loại tin (enum, lưu VARCHAR). */
    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, length = 20)
    private CategoryCode code;

    /** Tên hiển thị. */
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /** Slug dùng cho URL. */
    @Column(name = "slug", nullable = false, length = 60)
    private String slug;

    /** Mô tả ngắn. */
    @Column(name = "description", length = 255)
    private String description;

    /** Tên/định danh icon. */
    @Column(name = "icon", length = 50)
    private String icon;

    /** Danh sách trường bắt buộc theo loại tin, lưu JSON. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_fields", nullable = false, columnDefinition = "json")
    private String requiredFields;

    /** Danh sách trường tùy chọn theo loại tin, lưu JSON. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "optional_fields", nullable = false, columnDefinition = "json")
    private String optionalFields;

    /** Thứ tự hiển thị. */
    @Builder.Default
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    /** Còn hoạt động hay không. */
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /** Số tin đăng thuộc loại này (đếm sẵn). */
    @Builder.Default
    @Column(name = "listing_count", nullable = false)
    private Integer listingCount = 0;
}
