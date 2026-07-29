package com.webtro.modules.interaction.repository;

import com.webtro.modules.interaction.entity.ViewHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Truy vấn lịch sử xem tin (append-only). Không có xóa mềm nên không lọc {@code deleted_at}.
 */
@Repository
public interface ViewHistoryRepository extends JpaRepository<ViewHistory, Long> {

    /** Có lượt xem của cùng user cho cùng tin sau mốc thời gian (khử trùng lặp theo {@code view.dedup_minutes}). */
    boolean existsByListingIdAndUserIdAndViewedAtAfter(Long listingId, Long userId, Instant since);

    /** Có lượt xem của cùng IP cho cùng tin sau mốc thời gian (khử trùng lặp cho khách ẩn danh). */
    boolean existsByListingIdAndIpAddressAndViewedAtAfter(Long listingId, String ipAddress, Instant since);

    /** Lịch sử xem gần đây của một người dùng (phân trang). */
    Page<ViewHistory> findByUserIdOrderByViewedAtDesc(Long userId, Pageable pageable);

    /** Lượt xem mới nhất của một người kể từ {@code since} (giới hạn qua {@code Pageable}) — tín hiệu hành vi cho AI. */
    List<ViewHistory> findByUserIdAndViewedAtAfterOrderByViewedAtDesc(Long userId, Instant since, Pageable pageable);

    /** Id các tin (đã khử trùng) người dùng đã xem kể từ {@code since} — chống gợi ý lặp §9.2. */
    @Query("select distinct v.listingId from ViewHistory v where v.userId = :userId and v.viewedAt > :since")
    List<Long> findDistinctListingIdsByUserIdSince(@Param("userId") Long userId, @Param("since") Instant since);

    /** Người dùng đã từng xem tin nào chưa (quyết định cold-start). */
    boolean existsByUserId(Long userId);

    // ==================================================================
    //  Truy vấn phục vụ job nền (canonical mục 11) — thêm cho scheduler
    // ==================================================================

    /** Số người dùng (distinct) có lượt xem kể từ {@code since} — {@code RecommendationPrecomputeJob}. */
    @Query("SELECT COUNT(DISTINCT v.userId) FROM ViewHistory v "
            + "WHERE v.userId IS NOT NULL AND v.viewedAt >= :since")
    long countDistinctActiveUsersSince(@Param("since") Instant since);

    /** Id người dùng hoạt động (distinct) kể từ {@code since} — {@code NewMatchingListingNotifyJob}. */
    @Query("SELECT DISTINCT v.userId FROM ViewHistory v "
            + "WHERE v.userId IS NOT NULL AND v.viewedAt >= :since")
    List<Long> findDistinctActiveUserIdsSince(@Param("since") Instant since, Pageable pageable);

    /** Id các lượt xem cũ hơn {@code threshold} — {@code DataRetentionJob} xóa theo lô. */
    @Query("SELECT v.id FROM ViewHistory v WHERE v.viewedAt < :threshold ORDER BY v.id ASC")
    List<Long> findIdsViewedBefore(@Param("threshold") Instant threshold, Pageable pageable);
}
