package com.webtro.modules.admin.controller;

import com.webtro.common.ApiResponse;
import com.webtro.modules.admin.dto.response.DashboardResponse;
import com.webtro.modules.admin.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API Dashboard tổng quan (canonical 4.12.1). Chỉ Admin ({@code STATISTIC_VIEW}) — Moderator không
 * có quyền này ({@code [§1.2]}).
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "12. Admin - Dashboard", description = "Dashboard và thống kê")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Dashboard tổng quan", description = "10 chỉ số: người dùng, tin, report, doanh thu, thanh toán, AI, top khu vực/danh mục.")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.success(
                adminDashboardService.getDashboard(), "Lấy dashboard thành công"));
    }
}
