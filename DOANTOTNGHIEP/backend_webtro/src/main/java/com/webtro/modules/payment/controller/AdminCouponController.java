package com.webtro.modules.payment.controller;

import com.webtro.common.ApiResponse;
import com.webtro.common.PageResponse;
import com.webtro.modules.payment.dto.request.CouponRequest;
import com.webtro.modules.payment.dto.response.CouponResponse;
import com.webtro.modules.payment.service.PromotionService;
import com.webtro.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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

/**
 * API quản trị mã khuyến mãi (canonical 4.18.9–4.18.11). Quyền {@code PACKAGE_MANAGE}.
 * Không có DELETE — đặt {@code active = false} (canonical §6.1).
 */
@RestController
@RequestMapping("/api/admin/coupons")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "18. Admin - Payment", description = "Quản trị gói dịch vụ, thanh toán, coupon")
public class AdminCouponController {

    private final PromotionService promotionService;

    @GetMapping
    @Operation(summary = "Danh sách mã khuyến mãi")
    public ResponseEntity<ApiResponse<PageResponse<CouponResponse>>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean activeOnly,
            @RequestParam(required = false) Boolean validNow,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<CouponResponse> data =
                promotionService.adminListCoupons(keyword, activeOnly, validNow, pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy danh sách mã khuyến mãi thành công"));
    }

    @PostMapping
    @Operation(summary = "Tạo mã khuyến mãi")
    public ResponseEntity<ApiResponse<CouponResponse>> create(@Valid @RequestBody CouponRequest request) {
        CouponResponse data = promotionService.createCoupon(request, currentUserId());
        return ResponseEntity.created(URI.create("/api/admin/coupons/" + data.getId()))
                .body(ApiResponse.success(data, "Đã tạo mã khuyến mãi " + data.getCode()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Sửa mã khuyến mãi (mã bất biến)")
    public ResponseEntity<ApiResponse<CouponResponse>> update(
            @PathVariable Long id, @Valid @RequestBody CouponRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                promotionService.updateCoupon(id, request, currentUserId()), "Cập nhật mã khuyến mãi thành công"));
    }

    private Long currentUserId() {
        return SecurityUtils.getCurrentUserId().orElse(null);
    }
}
