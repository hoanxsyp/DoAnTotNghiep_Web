package com.webtro.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Thống kê doanh thu từ giao dịch {@code SUCCESS} theo khoảng ngày: tổng doanh thu, số giao dịch,
 * chuỗi theo thời gian (ngày/tháng) và phân rã theo gói dịch vụ.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "RevenueStatisticsResponse", description = "Thống kê doanh thu theo khoảng ngày")
public class RevenueStatisticsResponse {

    private LocalDate from;
    private LocalDate to;
    /** DAY hoặc MONTH. */
    private String granularity;

    /** Tổng doanh thu thực thu trong khoảng. */
    private BigDecimal totalRevenue;

    /** Tổng số giao dịch thành công trong khoảng. */
    private long transactionCount;

    /** Chuỗi doanh thu theo từng mốc thời gian. */
    private List<RevenuePoint> series;

    /** Phân rã doanh thu theo gói dịch vụ. */
    private List<PackageRevenue> byPackage;

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(name = "RevenuePoint", description = "Doanh thu tại một mốc thời gian")
    public static class RevenuePoint {
        /** Nhãn mốc thời gian: yyyy-MM-dd (DAY) hoặc yyyy-MM (MONTH). */
        private String bucket;
        private BigDecimal revenue;
        private long transactionCount;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(name = "PackageRevenue", description = "Doanh thu theo gói dịch vụ")
    public static class PackageRevenue {
        private Long packageId;
        private String packageCode;
        private String packageName;
        private BigDecimal revenue;
        private long transactionCount;
    }
}
