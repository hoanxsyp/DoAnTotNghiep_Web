package com.webtro.modules.listing.repository;

import com.webtro.modules.listing.entity.ListingAmenity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository cho {@link ListingAmenity}. Điều kiện {@code deleted_at IS NULL} viết tường minh.
 */
@Repository
public interface ListingAmenityRepository extends JpaRepository<ListingAmenity, Long> {

    /** Danh sách liên kết tiện ích còn sống của một tin. */
    @Query("SELECT la FROM ListingAmenity la WHERE la.listing.id = :listingId "
            + "AND la.deletedAt IS NULL")
    List<ListingAmenity> findByListingIdAlive(@Param("listingId") Long listingId);

    /** id các tiện ích còn gắn với tin (dùng để render bộ lọc / chi tiết). */
    @Query("SELECT la.amenityId FROM ListingAmenity la WHERE la.listing.id = :listingId "
            + "AND la.deletedAt IS NULL")
    List<Long> findAmenityIdsByListingIdAlive(@Param("listingId") Long listingId);
}
