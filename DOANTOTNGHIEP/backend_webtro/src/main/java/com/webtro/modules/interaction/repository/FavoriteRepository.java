package com.webtro.modules.interaction.repository;

import com.webtro.modules.interaction.entity.Favorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Truy vấn tin đã lưu (favorites). Mọi truy vấn công khai lọc {@code deleted_at IS NULL}.
 */
@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    /** Lấy bản ghi lưu tin còn hiệu lực của một người cho một tin. */
    Optional<Favorite> findByUserIdAndListingIdAndDeletedAtIsNull(Long userId, Long listingId);

    /** Kiểm tra người dùng đã lưu tin này chưa (chống lưu trùng — 409 CONFLICT). */
    boolean existsByUserIdAndListingIdAndDeletedAtIsNull(Long userId, Long listingId);

    /** Danh sách tin đã lưu của một người (phân trang). */
    Page<Favorite> findByUserIdAndDeletedAtIsNull(Long userId, Pageable pageable);

    /** Đếm số lượt lưu còn hiệu lực của một tin (đồng bộ {@code listings.favorite_count}). */
    long countByListingIdAndDeletedAtIsNull(Long listingId);

    /** Tin lưu mới nhất của một người kể từ {@code since} (giới hạn qua {@code Pageable}) — tín hiệu hành vi w=3. */
    List<Favorite> findByUserIdAndCreatedAtAfterAndDeletedAtIsNullOrderByCreatedAtDesc(
            Long userId, Instant since, Pageable pageable);

    /** Người dùng đã lưu tin nào chưa (quyết định cold-start). */
    boolean existsByUserIdAndDeletedAtIsNull(Long userId);

    /** Trong {@code listingIds}, những tin người dùng đang lưu — cờ {@code favoritedByMe}. */
    @Query("select f.listingId from Favorite f "
            + "where f.userId = :userId and f.listingId in :listingIds and f.deletedAt is null")
    List<Long> findFavoritedListingIds(@Param("userId") Long userId,
                                       @Param("listingIds") Collection<Long> listingIds);

    /** Lượt lưu hiện còn hiệu lực theo ngày của một tin trong khoảng [from, to). */
    @Query(value = """
            SELECT DATE(f.created_at) AS day, COUNT(*) AS total
            FROM favorites f
            WHERE f.listing_id = :listingId
              AND f.deleted_at IS NULL
              AND f.created_at >= :from
              AND f.created_at < :to
            GROUP BY DATE(f.created_at)
            """, nativeQuery = true)
    List<Object[]> countDailyFavoritesForListingBetween(@Param("listingId") Long listingId,
                                                        @Param("from") Instant from,
                                                        @Param("to") Instant to);
}
