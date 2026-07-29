package com.webtro.modules.ai.repository;

import com.webtro.modules.ai.entity.PredictionHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Truy vấn lịch sử dự đoán giá ({@code prediction_histories}, append-only). Không có xóa mềm.
 *
 * <p>{@link JpaSpecificationExecutor} phục vụ báo cáo/đánh giá chất lượng AI lọc động ở Admin.
 */
@Repository
public interface PredictionHistoryRepository
        extends JpaRepository<PredictionHistory, Long>, JpaSpecificationExecutor<PredictionHistory> {

    /** Lịch sử dự đoán của một tin (mới nhất trước). */
    List<PredictionHistory> findByListingIdOrderByCreatedAtDesc(Long listingId);

    /** Dự đoán gần nhất của một tin. */
    Optional<PredictionHistory> findFirstByListingIdOrderByCreatedAtDesc(Long listingId);

    /** Lịch sử dự đoán do một người dùng yêu cầu (phân trang). */
    Page<PredictionHistory> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** Các dự đoán bị đánh cờ lệch giá (danh sách tin nghi ngờ cho Admin). */
    Page<PredictionHistory> findByIsFlaggedTrueOrderByCreatedAtDesc(Pageable pageable);
}
