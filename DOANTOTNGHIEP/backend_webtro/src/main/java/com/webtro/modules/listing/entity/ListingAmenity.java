package com.webtro.modules.listing.entity;

import com.webtro.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Bảng nối tin đăng &lt;-&gt; tiện ích [§3.3]. Bảng nghiệp vụ (có xóa mềm, audit).
 *
 * <p>{@code listing} cùng module → map {@code @ManyToOne(LAZY)}. {@code amenity_id} trỏ sang
 * bảng tra cứu {@code amenities} ở module khác nên chỉ giữ {@code Long} (canonical luật 8).
 */
@Entity
@Table(
        name = "listing_amenities",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_listing_amenities_listing_amenity",
                columnNames = {"listing_id", "amenity_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ListingAmenity extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    /** amenities.id (chéo module → chỉ giữ Long). */
    @Column(name = "amenity_id", nullable = false)
    private Long amenityId;
}
