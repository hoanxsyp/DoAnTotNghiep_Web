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
 * Thống kê theo khoảng ngày (canonical 4.12.2): chuỗi thời gian + tổng + các tỷ lệ.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "StatisticsResponse", description = "Thống kê theo khoảng ngày")
public class StatisticsResponse {

    private LocalDate from;
    private LocalDate to;
    private String granularity;
    private List<DailyPoint> series;
    private Totals totals;
    private Rates rates;

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(name = "StatisticsDailyPoint")
    public static class DailyPoint {
        private LocalDate date;
        private long newUsers;
        private long newListings;
        private long approvedListings;
        private long rejectedListings;
        private long closedListings;
        private BigDecimal revenue;
        private long newReports;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(name = "StatisticsTotals")
    public static class Totals {
        private long newUsers;
        private long newListings;
        private long approvedListings;
        private long rejectedListings;
        private long closedListings;
        private BigDecimal revenue;
        private long newReports;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(name = "StatisticsRates")
    public static class Rates {
        private BigDecimal approvalRatePercent;
        private BigDecimal rejectionRatePercent;
        private BigDecimal successfulRentalRatePercent;
    }
}
