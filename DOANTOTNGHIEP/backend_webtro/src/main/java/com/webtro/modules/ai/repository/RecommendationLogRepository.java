package com.webtro.modules.ai.repository;

import com.webtro.modules.ai.entity.RecommendationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Truy vấn nhật ký gợi ý ({@code recommendation_logs}, append-only). Không có xóa mềm.
 */
@Repository
public interface RecommendationLogRepository extends JpaRepository<RecommendationLog, Long> {

    /** Các dòng thuộc cùng một lô gợi ý. */
    List<RecommendationLog> findByBatchId(String batchId);

    /** Đã gợi ý tin này cho user sau mốc thời gian chưa (chống gợi ý lặp {@code [§9.2]}). */
    boolean existsByUserIdAndListingIdAndCreatedAtAfter(Long userId, Long listingId, Instant since);

    /** Các id tin đã gợi ý cho user gần đây (dựng danh sách loại trừ khi gợi ý lô mới). */
    List<RecommendationLog> findByUserIdAndCreatedAtAfter(Long userId, Instant since);
}
