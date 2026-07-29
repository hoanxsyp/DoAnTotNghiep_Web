package com.webtro.modules.listing.repository;

import com.webtro.modules.listing.entity.ListingImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository cho {@link ListingImage}. Điều kiện {@code deleted_at IS NULL} viết tường minh.
 */
@Repository
public interface ListingImageRepository extends JpaRepository<ListingImage, Long> {

    /** Danh sách ảnh còn sống của một tin, sắp theo thứ tự hiển thị. */
    @Query("SELECT i FROM ListingImage i WHERE i.listing.id = :listingId AND i.deletedAt IS NULL "
            + "ORDER BY i.displayOrder ASC")
    List<ListingImage> findByListingIdAlive(@Param("listingId") Long listingId);

    /** Ảnh đại diện (is_primary = true) còn sống của một tin. */
    @Query("SELECT i FROM ListingImage i WHERE i.listing.id = :listingId "
            + "AND i.isPrimary = true AND i.deletedAt IS NULL")
    Optional<ListingImage> findPrimaryByListingIdAlive(@Param("listingId") Long listingId);
}
