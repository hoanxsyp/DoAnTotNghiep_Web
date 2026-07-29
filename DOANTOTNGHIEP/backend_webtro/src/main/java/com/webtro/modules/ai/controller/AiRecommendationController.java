package com.webtro.modules.ai.controller;

import com.webtro.common.ApiResponse;
import com.webtro.modules.ai.dto.request.RecommendationRequest;
import com.webtro.modules.ai.dto.response.RecommendationResponse;
import com.webtro.modules.ai.service.RecommendationService;
import com.webtro.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API gợi ý tin đăng (AI-04, docs/03 mục 7.2). Công khai: khách (cold start) và người đăng nhập
 * (cá nhân hóa) đều gọi được.
 */
@Tag(name = "AI - Gợi ý tin đăng", description = "Gợi ý tin theo hành vi hoặc cold start")
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiRecommendationController {

    private final RecommendationService recommendationService;

    @Operation(summary = "Gợi ý tin đăng cho bạn",
            description = "Người đăng nhập → cá nhân hóa theo hành vi; khách/thiếu dữ liệu → cold "
                    + "start (tin mới + phổ biến theo khu vực). Luôn ghi RecommendationLog để giải thích. "
                    + "Chỉ trả tin công khai.")
    @PostMapping("/recommendations")
    public ResponseEntity<ApiResponse<RecommendationResponse>> recommend(
            @Valid @RequestBody RecommendationRequest request,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        RecommendationResponse data = recommendationService.recommend(request, principal, sessionId);
        String message = Boolean.TRUE.equals(data.getColdStart())
                ? "Tin mới và phổ biến dành cho bạn" : "Gợi ý cho bạn";
        return ResponseEntity.ok(ApiResponse.success(data, message));
    }
}
