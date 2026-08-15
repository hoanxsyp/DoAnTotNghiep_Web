package com.webtro.modules.payment.controller;

import com.webtro.common.ApiResponse;
import com.webtro.modules.payment.dto.request.PackageRequest;
import com.webtro.modules.payment.dto.request.TogglePackageRequest;
import com.webtro.modules.payment.dto.response.PromotionPackageResponse;
import com.webtro.modules.payment.service.PromotionService;
import com.webtro.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * API quản trị gói dịch vụ (canonical 4.18.1–4.18.4). Quyền {@code PACKAGE_MANAGE} (chỉ Admin).
 * Không có DELETE — chỉ bật/tắt (canonical §6.1).
 */
@RestController
@RequestMapping("/api/admin/promotion-packages")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "18. Admin - Payment", description = "Quản trị gói dịch vụ, thanh toán, coupon")
public class AdminPackageController {

    private final PromotionService promotionService;

    @GetMapping
    @Operation(summary = "Danh sách gói (kể cả đã tắt) + số liệu")
    public ResponseEntity<ApiResponse<List<PromotionPackageResponse>>> list(
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(ApiResponse.success(
                promotionService.adminListPackages(activeOnly), "Lấy danh sách gói dịch vụ thành công"));
    }

    @PostMapping
    @Operation(summary = "Tạo gói dịch vụ")
    public ResponseEntity<ApiResponse<PromotionPackageResponse>> create(
            @Valid @RequestBody PackageRequest request) {
        PromotionPackageResponse data = promotionService.createPackage(request, currentUserId());
        return ResponseEntity.created(URI.create("/api/admin/promotion-packages/" + data.getId()))
                .body(ApiResponse.success(data, "Tạo gói dịch vụ thành công"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Sửa gói dịch vụ (mã bất biến)")
    public ResponseEntity<ApiResponse<PromotionPackageResponse>> update(
            @PathVariable Long id, @Valid @RequestBody PackageRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                promotionService.updatePackage(id, request, currentUserId()), "Cập nhật gói dịch vụ thành công"));
    }

    @PutMapping("/{id}/toggle")
    @Operation(summary = "Bật/tắt bán gói dịch vụ")
    public ResponseEntity<ApiResponse<PromotionPackageResponse>> toggle(
            @PathVariable Long id, @Valid @RequestBody TogglePackageRequest request) {
        PromotionPackageResponse data = promotionService.togglePackage(id, request, currentUserId());
        String msg = Boolean.TRUE.equals(data.getActive())
                ? "Đã bật bán gói dịch vụ"
                : "Đã tắt gói dịch vụ. Các gói đang hiệu lực không bị ảnh hưởng.";
        return ResponseEntity.ok(ApiResponse.success(data, msg));
    }

    private Long currentUserId() {
        return SecurityUtils.getCurrentUserId().orElse(null);
    }
}
