package com.webtro.modules.ai.service;

import com.webtro.common.PageResponse;
import com.webtro.modules.ai.dto.request.PricePredictionRequest;
import com.webtro.modules.ai.dto.response.PricePredictionHistoryResponse;
import com.webtro.modules.ai.dto.response.PricePredictionResponse;
import org.springframework.data.domain.Pageable;

/**
 * Nghiệp vụ dự đoán giá thuê (AI-06, canonical mục 10.4, §9.4).
 */
public interface PriceEstimationService {

    /**
     * Dự đoán giá cho một cấu hình phòng. Thiếu mẫu → 422 {@code AI_INSUFFICIENT_DATA} (không dự
     * đoán). Lệch giá vượt ngưỡng → ghi cờ, cảnh báo mềm, TUYỆT ĐỐI không chặn. Lưu
     * {@code PredictionHistory} mọi lần.
     */
    PricePredictionResponse predict(PricePredictionRequest request, Long userId);

    /**
     * Lịch sử dự đoán giá của một tin (chủ tin, hoặc người có {@code AI_LOG_VIEW}).
     *
     * @param hasLogView người gọi có quyền {@code AI_LOG_VIEW} (bỏ qua kiểm chủ sở hữu)
     */
    PageResponse<PricePredictionHistoryResponse> history(Long listingId, Long requesterId,
                                                         boolean hasLogView, Pageable pageable);
}
