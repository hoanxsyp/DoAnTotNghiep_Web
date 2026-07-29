package com.webtro.modules.interaction.repository;

import com.webtro.modules.interaction.entity.SearchHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Truy vấn lịch sử tìm kiếm (append-only).
 */
@Repository
public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {

    /** Lịch sử tìm kiếm gần đây của một người dùng (phân trang). */
    Page<SearchHistory> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** Người dùng đã từng tìm kiếm chưa (quyết định cold-start). */
    boolean existsByUserId(Long userId);

    // ==================================================================
    //  Truy vấn phục vụ job nền (canonical mục 11) — thêm cho scheduler
    // ==================================================================

    /** Số người dùng (distinct) có lượt tìm kiếm kể từ {@code since} — {@code RecommendationPrecomputeJob}. */
    @Query("SELECT COUNT(DISTINCT s.userId) FROM SearchHistory s "
            + "WHERE s.userId IS NOT NULL AND s.createdAt >= :since")
    long countDistinctActiveUsersSince(@Param("since") Instant since);

    /** Id các lượt tìm kiếm cũ hơn {@code threshold} — {@code DataRetentionJob} xóa theo lô. */
    @Query("SELECT s.id FROM SearchHistory s WHERE s.createdAt < :threshold ORDER BY s.id ASC")
    List<Long> findIdsCreatedBefore(@Param("threshold") Instant threshold, Pageable pageable);
}
