package com.webtro.modules.listing.entity;

import com.webtro.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Ảnh của tin đăng [§3.3][§11.9]. Bảng nghiệp vụ (có xóa mềm, audit).
 *
 * <p>Quan hệ {@link Listing} cùng module nên map {@code @ManyToOne(LAZY)}.
 */
@Entity
@Table(name = "listing_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ListingImage extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @Column(name = "url", nullable = false, length = 500)
    private String url;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "is_primary", nullable = false)
    @lombok.Builder.Default
    private Boolean isPrimary = false;

    @Column(name = "display_order", nullable = false)
    @lombok.Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "original_name", length = 255)
    private String originalName;

    /** Kích thước byte; ràng buộc DB: 0 &lt; file_size ≤ 5MB. */
    @Column(name = "file_size", nullable = false)
    private Integer fileSize;

    /** MIME type (image/jpeg | image/png | image/webp) — ràng buộc CHECK ở DB. */
    @Column(name = "content_type", nullable = false, length = 30)
    private String contentType;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;
}
