package com.webtro.modules.ai.controller;

import com.webtro.common.ApiResponse;
import com.webtro.common.PageResponse;
import com.webtro.constant.PermissionCode;
import com.webtro.modules.ai.dto.request.PricePredictionRequest;
import com.webtro.modules.ai.dto.response.PricePredictionHistoryResponse;
import com.webtro.modules.ai.dto.response.PricePredictionResponse;
import com.webtro.modules.ai.service.PriceEstimationService;
import com.webtro.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API dự đoán giá thuê (AI-06, docs/03 mục 7.4). Dự đoán cần quyền {@code LISTING_CREATE}; xem lịch
 * sử cần chủ tin ({@code LISTING_UPDATE_OWN}) hoặc {@code AI_LOG_VIEW}.
 */
@Tag(name = "AI - Dự đoán giá", description = "Ước lượng giá thuê tham khảo (không chặn đăng tin)")
@RestController
@RequestMapping("/api/ai/price-prediction")
@RequiredArgsConstructor
public class AiPriceController {

    private final PriceEstimationService priceEstimationService;

    @Operation(summary = "Dự đoán giá thuê",
            description = "So sánh tin tương đương + điều chỉnh hedonic. Thiếu mẫu → 422 "
                    + "AI_INSUFFICIENT_DATA. Lệch giá lớn → cảnh báo mềm, KHÔNG chặn đăng tin. "
                    + "Lưu PredictionHistory mọi lần.")
    @PostMapping
    @PreAuthorize("hasAuthority('LISTING_CREATE')")
    public ResponseEntity<ApiResponse<PricePredictionResponse>> predict(
            @Valid @RequestBody PricePredictionRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        PricePredictionResponse data = priceEstimationService.predict(request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(data, "Dự đoán giá thành công"));
    }

    @Operation(summary = "Lịch sử dự đoán giá của một tin",
            description = "Chủ tin (LISTING_UPDATE_OWN) hoặc người có AI_LOG_VIEW.")
    @GetMapping("/histories")
    @PreAuthorize("hasAnyAuthority('LISTING_UPDATE_OWN','AI_LOG_VIEW')")
    public ResponseEntity<ApiResponse<PageResponse<PricePredictionHistoryResponse>>> histories(
            @RequestParam Long listingId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails principal) {
        boolean hasLogView = principal.hasPermission(PermissionCode.AI_LOG_VIEW);
        PageResponse<PricePredictionHistoryResponse> data =
                priceEstimationService.history(listingId, principal.getId(), hasLogView, pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy lịch sử dự đoán giá thành công"));
    }
}
