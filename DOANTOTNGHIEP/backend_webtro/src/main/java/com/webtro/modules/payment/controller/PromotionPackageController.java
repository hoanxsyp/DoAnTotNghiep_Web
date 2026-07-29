package com.webtro.modules.payment.controller;

import com.webtro.common.ApiResponse;
import com.webtro.modules.payment.dto.response.PromotionPackageResponse;
import com.webtro.modules.payment.service.PromotionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API công khai danh sách/chi tiết gói dịch vụ đẩy tin (canonical 4.9.1, 4.9.2).
 * Danh sách gói đang bật được cache Redis ({@code promotionPackages}).
 */
@RestController
@RequestMapping("/api/promotion-packages")
@RequiredArgsConstructor
@Tag(name = "09. Payment & Promotion", description = "Thanh toán, gói dịch vụ, khuyến mãi")
public class PromotionPackageController {

    private final PromotionService promotionService;

    @GetMapping
    @Operation(summary = "Danh sách gói dịch vụ đang bật (công khai)")
    public ResponseEntity<ApiResponse<List<PromotionPackageResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(
                promotionService.getActivePackages(), "Lấy danh sách gói dịch vụ thành công"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết một gói dịch vụ (công khai)")
    public ResponseEntity<ApiResponse<PromotionPackageResponse>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                promotionService.getPackage(id), "Lấy chi tiết gói dịch vụ thành công"));
    }
}
