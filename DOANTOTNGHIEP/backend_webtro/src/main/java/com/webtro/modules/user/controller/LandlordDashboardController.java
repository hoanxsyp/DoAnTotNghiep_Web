package com.webtro.modules.user.controller;

import com.webtro.common.ApiResponse;
import com.webtro.modules.user.dto.response.LandlordDashboardResponse;
import com.webtro.modules.user.service.LandlordDashboardService;
import com.webtro.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API tổng quan chủ trọ (canonical 4.4). Chỉ chủ trọ ({@code LISTING_CREATE}).
 */
@RestController
@RequestMapping("/api/landlord")
@RequiredArgsConstructor
@Tag(name = "02. User", description = "Hồ sơ người dùng, hồ sơ chủ trọ, ảnh đại diện")
public class LandlordDashboardController {

    private final LandlordDashboardService landlordDashboardService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('LISTING_CREATE')")
    @Operation(summary = "Tổng quan chủ trọ",
            description = "Số tin theo trạng thái, tổng lượt xem/lưu/liên hệ, điểm uy tín, tỷ lệ phản hồi của chủ trọ đang đăng nhập.")
    public ResponseEntity<ApiResponse<LandlordDashboardResponse>> getDashboard(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        LandlordDashboardResponse data = landlordDashboardService.getDashboard(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy tổng quan chủ trọ thành công"));
    }
}
