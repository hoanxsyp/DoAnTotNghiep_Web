package com.webtro.modules.ai.service;

import com.webtro.modules.ai.dto.request.RecommendationRequest;
import com.webtro.modules.ai.dto.response.RecommendationResponse;
import com.webtro.security.CustomUserDetails;

/**
 * Nghiệp vụ gợi ý tin đăng (AI-04, canonical mục 10.2, §9.2).
 */
public interface RecommendationService {

    /**
     * Sinh danh sách gợi ý cho người dùng (đăng nhập → cá nhân hóa; khách/thiếu dữ liệu → cold start).
     * Ghi {@code RecommendationLog} mọi lần để giải thích và đánh giá hiệu quả.
     *
     * @param request   ngữ cảnh + tham số
     * @param principal người dùng đăng nhập (có thể {@code null})
     * @param sessionId định danh khách ẩn danh (có thể {@code null})
     */
    RecommendationResponse recommend(RecommendationRequest request, CustomUserDetails principal,
                                     String sessionId);
}
