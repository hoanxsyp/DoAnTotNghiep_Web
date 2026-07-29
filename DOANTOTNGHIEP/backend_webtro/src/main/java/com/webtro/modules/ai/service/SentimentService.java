package com.webtro.modules.ai.service;

import com.webtro.modules.ai.dto.request.SentimentAnalyzeRequest;
import com.webtro.modules.ai.dto.response.SentimentResponse;

/**
 * Nghiệp vụ phân tích cảm xúc bình luận (AI-01, canonical mục 10.1, §9.1).
 */
public interface SentimentService {

    /**
     * Endpoint chẩn đoán của Admin/Moderator: phân tích lại một bình luận có sẵn hoặc thử nghiệm
     * văn bản. {@code persist = true} (chỉ với commentId) → ghi đè {@code sentiment_results} và tính
     * lại điểm uy tín.
     */
    SentimentResponse analyze(SentimentAnalyzeRequest request, Long actorId);

    /**
     * Xử lý cảm xúc cho một bình luận mới/được sửa — gọi bởi listener sau khi commit
     * ({@code @Async}). Mở transaction MỚI (REQUIRES_NEW): lưu {@link com.webtro.modules.ai.entity.SentimentResult}
     * (is_latest), ghi ngược sentiment vào bình luận, cập nhật bộ đếm tin, tính lại điểm uy tín, và
     * kiểm ngưỡng tiêu cực → {@code FLAG_NEED_REVIEW} + cảnh báo. Engine lỗi/timeout → lưu
     * {@code PENDING_ANALYSIS} (bình luận vẫn được giữ, §9.1).
     */
    void processCommentSentiment(Long commentId, Long listingId);
}
