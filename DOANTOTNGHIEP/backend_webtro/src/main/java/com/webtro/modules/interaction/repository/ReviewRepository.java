package com.webtro.modules.interaction.repository;

import com.webtro.common.enums.ReviewStatus;
import com.webtro.modules.interaction.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Truy vấn đánh giá. Mọi truy vấn công khai lọc {@code deleted_at IS NULL}. Dùng
 * {@link JpaSpecificationExecutor} cho lọc động ở màn hình kiểm duyệt (trạng thái, sao, tin, chủ trọ).
 */
@Repository
public interface ReviewRepository extends JpaRepository<Review, Long>, JpaSpecificationExecutor<Review> {

    /** Đánh giá còn hiệu lực của một người cho một tin (chống đánh giá trùng — {@code uk_reviews_user_listing}). */
    Optional<Review> findByUserIdAndListingIdAndDeletedAtIsNull(Long userId, Long listingId);

    /** Người dùng đã đánh giá tin này chưa (409 CONFLICT nếu đã có). */
    boolean existsByUserIdAndListingIdAndDeletedAtIsNull(Long userId, Long listingId);

    /** Danh sách đánh giá hiển thị của một tin, mới nhất trước. */
    Page<Review> findByListingIdAndStatusAndDeletedAtIsNull(Long listingId, ReviewStatus status, Pageable pageable);

    /** Danh sách đánh giá hiển thị của một chủ trọ. */
    @Query(value = """
            SELECT r
            FROM Review r, Listing l
            WHERE l.id = r.listingId
              AND l.ownerId = :landlordId
              AND r.status = :status
              AND r.deletedAt IS NULL
            ORDER BY r.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(r)
            FROM Review r, Listing l
            WHERE l.id = r.listingId
              AND l.ownerId = :landlordId
              AND r.status = :status
              AND r.deletedAt IS NULL
            """)
    Page<Review> findByLandlordIdAndStatusAndDeletedAtIsNull(
            @Param("landlordId") Long landlordId, @Param("status") ReviewStatus status, Pageable pageable);

    /** Đếm đánh giá hiển thị của một tin (đồng bộ {@code listings.review_count}). */
    long countByListingIdAndStatusAndDeletedAtIsNull(Long listingId, ReviewStatus status);
}
