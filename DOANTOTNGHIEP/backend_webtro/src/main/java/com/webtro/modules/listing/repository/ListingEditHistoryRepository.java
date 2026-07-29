package com.webtro.modules.listing.repository;

import com.webtro.modules.listing.entity.ListingEditHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository cho {@link ListingEditHistory}. Bảng append-only, không có xóa mềm.
 */
@Repository
public interface ListingEditHistoryRepository extends JpaRepository<ListingEditHistory, Long> {

    /** Lịch sử sửa của một tin, mới nhất trước. */
    @Query("SELECT h FROM ListingEditHistory h WHERE h.listing.id = :listingId "
            + "ORDER BY h.createdAt DESC")
    List<ListingEditHistory> findByListingIdOrderByCreatedAtDesc(@Param("listingId") Long listingId);

    /** Các thay đổi thuộc cùng một lần sửa (theo edit_batch_id). */
    List<ListingEditHistory> findByEditBatchIdOrderByCreatedAtAsc(String editBatchId);
}
