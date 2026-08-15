package com.webtro.modules.payment.controller;

import com.webtro.common.ApiResponse;
import com.webtro.modules.payment.dto.request.ValidateCouponRequest;
import com.webtro.modules.payment.dto.response.CouponValidationResponse;
import com.webtro.modules.payment.service.PromotionService;
import com.webtro.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API kiểm tra mã khuyến mãi trước khi thanh toán (canonical 4.9.9).
 * Chỉ kiểm tra, KHÔNG tiêu mã; có rate limit chống dò mã (xử lý ở service).
 */
@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
@Tag(name = "09. Payment & Promotion", description = "Thanh toán, gói dịch vụ, khuyến mãi")
public class CouponController {

    private final PromotionService promotionService;

    @PostMapping("/validate")
    @PreAuthorize("hasAnyRole('LANDLORD','ADMIN')")
    @Operation(summary = "Kiểm tra mã khuyến mãi cho một gói")
    public ResponseEntity<ApiResponse<CouponValidationResponse>> validate(
            @Valid @RequestBody ValidateCouponRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        CouponValidationResponse data = promotionService.validateCoupon(request, user.getId());
        return ResponseEntity.ok(ApiResponse.success(data, "Áp dụng mã khuyến mãi thành công"));
    }
}
