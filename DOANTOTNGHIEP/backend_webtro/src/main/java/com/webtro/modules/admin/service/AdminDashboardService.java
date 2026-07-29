package com.webtro.modules.admin.service;

import com.webtro.modules.admin.dto.response.DashboardResponse;
import com.webtro.modules.admin.dto.response.RevenueStatisticsResponse;
import com.webtro.modules.admin.dto.response.StatisticsResponse;

import java.time.LocalDate;

/**
 * Dashboard & thống kê cho Admin (canonical 4.12) — quyền {@code STATISTIC_VIEW} (chỉ Admin).
 * Tổng hợp số liệu chéo module bằng các count/sum query chỉ-đọc.
 */
public interface AdminDashboardService {

    /** Dashboard tổng quan 10 chỉ số (canonical 4.12.1). */
    DashboardResponse getDashboard();

    /** Thống kê theo khoảng ngày (canonical 4.12.2). */
    StatisticsResponse getStatistics(LocalDate from, LocalDate to, String granularity);

    /**
     * Thống kê doanh thu từ giao dịch {@code SUCCESS} theo khoảng ngày, nhóm theo ngày ({@code DAY})
     * hoặc tháng ({@code MONTH}); kèm tổng doanh thu, số giao dịch và phân rã theo gói dịch vụ.
     */
    RevenueStatisticsResponse getRevenueStatistics(LocalDate from, LocalDate to, String granularity);
}
