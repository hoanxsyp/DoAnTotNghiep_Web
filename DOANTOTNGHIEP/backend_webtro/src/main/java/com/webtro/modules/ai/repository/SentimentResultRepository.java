package com.webtro.modules.ai.repository;

import com.webtro.common.enums.SentimentAction;
import com.webtro.common.enums.SentimentLabel;
import com.webtro.modules.ai.entity.SentimentResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Truy vấn kết quả phân tích cảm xúc ({@code sentiment_results}, append-only).
 *
 * <p>Bảng không có xóa mềm nên không lọc {@code deleted_at}; phiên bản hiện hành lọc bằng
 * {@code is_latest = TRUE}. {@link JpaSpecificationExecutor} phục vụ hàng đợi cảnh báo AI ở Admin.
 */
@Repository
public interface SentimentResultRepository
        extends JpaRepository<SentimentResult, Long>, JpaSpecificationExecutor<SentimentResult> {

    /** Bản phân tích hiện hành của một bình luận (đúng 1 do cột sinh latest_uk). */
    Optional<SentimentResult> findByCommentIdAndIsLatestTrue(Long commentId);

    /** Lịch sử các phiên bản phân tích của một bình luận. */
    List<SentimentResult> findByCommentIdOrderByCreatedAtDesc(Long commentId);

    /** Đếm bình luận hiện hành theo nhãn của một tin (tính tỷ lệ tiêu cực, ngưỡng 40%/50%). */
    long countByListingIdAndLabelAndIsLatestTrue(Long listingId, SentimentLabel label);

    /** Đếm toàn bộ bình luận hiện hành của một tin. */
    long countByListingIdAndIsLatestTrue(Long listingId);

    /** Hàng đợi tin bị AI đề xuất xử lý (WATCH/NEED_REVIEW), chỉ bản hiện hành. */
    Page<SentimentResult> findBySuggestedActionAndIsLatestTrue(SentimentAction suggestedAction, Pageable pageable);

    /**
     * Bình luận có bản phân tích hiện hành đang {@code PENDING_ANALYSIS} và còn dưới ngưỡng thử lại
     * ({@code ai.sentiment.max_retry}) — ứng viên cho {@code SentimentRetryJob}. Sắp cũ nhất trước.
     */
    List<SentimentResult> findByLabelAndIsLatestTrueAndRetryCountLessThanOrderByCreatedAtAsc(
            SentimentLabel label, int maxRetry, Pageable pageable);
}
