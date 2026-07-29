package com.webtro.modules.admin.controller;

import com.webtro.common.ApiResponse;
import com.webtro.modules.admin.dto.response.RevenueStatisticsResponse;
import com.webtro.modules.admin.dto.response.StatisticsResponse;
import com.webtro.modules.admin.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * API thống kê theo khoảng ngày (canonical 4.12.2). Chỉ Admin ({@code STATISTIC_VIEW}).
 */
@RestController
@RequestMapping("/api/admin/statistics")
@RequiredArgsConstructor
@Tag(name = "12. Admin - Dashboard", description = "Dashboard và thống kê")
public class AdminStatisticController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping
    @PreAuthorize("hasAuthority('STATISTIC_VIEW')")
    @Operation(summary = "Thống kê chi tiết", description = "Chuỗi theo ngày + tổng + tỷ lệ duyệt/từ chối/thuê thành công theo khoảng ngày.")
    public ResponseEntity<ApiResponse<StatisticsResponse>> getStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "DAY") String granularity) {
        StatisticsResponse data = adminDashboardService.getStatistics(from, to, granularity);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy thống kê thành công"));
    }

    @GetMapping("/revenue")
    @PreAuthorize("hasAuthority('STATISTIC_VIEW')")
    @Operation(summary = "Thống kê doanh thu",
            description = "Doanh thu từ giao dịch SUCCESS theo khoảng ngày; nhóm theo ngày (DAY) hoặc tháng (MONTH); "
                    + "kèm tổng doanh thu, số giao dịch và phân rã doanh thu theo gói dịch vụ.")
    public ResponseEntity<ApiResponse<RevenueStatisticsResponse>> getRevenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "DAY") String granularity) {
        RevenueStatisticsResponse data = adminDashboardService.getRevenueStatistics(from, to, granularity);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy thống kê doanh thu thành công"));
    }
}
