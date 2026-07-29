package com.webtro.modules.ai.controller;

import com.webtro.common.ApiResponse;
import com.webtro.common.enums.RecommendationSource;
import com.webtro.modules.ai.dto.request.RecommendationRequest;
import com.webtro.modules.ai.dto.response.RecommendationResponse;
import com.webtro.modules.ai.service.RecommendationService;
import com.webtro.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API gợi ý tin đăng công khai theo phong cách REST-GET (AI-04) — path {@code /api/listings/suggested}
 * để FE dùng chung với các endpoint danh sách tin. Ủy quyền toàn bộ nghiệp vụ cho
 * {@link RecommendationService} (giống {@link AiRecommendationController} nhưng nhận tham số qua query
 * cho tiện gọi từ trang chủ / trang tìm kiếm / trang 404).
 *
 * <p>Công khai: khách (cold start) và người đăng nhập (cá nhân hóa) đều gọi được; chỉ trả tin công khai.
 * Đặt trong module {@code ai} để tránh phụ thuộc ngược từ module {@code listing} sang {@code ai}.
 */
@Tag(name = "AI - Gợi ý tin đăng", description = "Gợi ý tin theo hành vi hoặc cold start")
@RestController
@RequestMapping("/api/listings")
@RequiredArgsConstructor
public class SuggestedListingController {

    private final RecommendationService recommendationService;

    @Operation(summary = "Gợi ý tin đăng (GET)",
            description = "Trả danh sách tin gợi ý dựa trên ngữ cảnh (HOMEPAGE, LOW_RESULT_SEARCH, "
                    + "SIMILAR_LISTING, AFTER_FAVORITE). Người đăng nhập → cá nhân hóa; khách/thiếu dữ "
                    + "liệu → cold start. Luôn ghi RecommendationLog. Chỉ trả tin công khai.")
    @GetMapping("/suggested")
    public ResponseEntity<ApiResponse<RecommendationResponse>> suggested(
            @RequestParam(name = "source", defaultValue = "HOMEPAGE") RecommendationSource source,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "listingId", required = false) Long listingId,
            @RequestParam(name = "provinceId", required = false) Long provinceId,
            @RequestParam(name = "districtId", required = false) Long districtId,
            @RequestParam(name = "excludeListingIds", required = false) List<Long> excludeListingIds,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @AuthenticationPrincipal CustomUserDetails principal) {

        RecommendationRequest request = RecommendationRequest.builder()
                .source(source)
                .size(size)
                .listingId(listingId)
                .provinceId(provinceId)
                .districtId(districtId)
                .excludeListingIds(excludeListingIds)
                .build();

        RecommendationResponse data = recommendationService.recommend(request, principal, sessionId);
        String message = Boolean.TRUE.equals(data.getColdStart())
                ? "Tin mới và phổ biến dành cho bạn" : "Gợi ý cho bạn";
        return ResponseEntity.ok(ApiResponse.success(data, message));
    }
}
