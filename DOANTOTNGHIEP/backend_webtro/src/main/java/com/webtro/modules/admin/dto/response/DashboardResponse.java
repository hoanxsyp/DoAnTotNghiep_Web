package com.webtro.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Dashboard tổng quan cho Admin (canonical 4.12.1) — phủ đủ 10 chỉ số ở §10.1: người dùng, chủ trọ,
 * tin theo trạng thái, tin mới ngày/tuần/tháng, report chờ, doanh thu gói, tỷ lệ thanh toán, cảnh
 * báo AI, top khu vực, top danh mục.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "DashboardResponse", description = "Dashboard tổng quan Admin")
public class DashboardResponse {

    private Users users;
    private Listings listings;
    private Reports reports;
    private Revenue revenue;
    private Payments payments;
    private AiAlerts aiAlerts;
    private List<TopProvince> topProvinces;
    private List<TopCategory> topCategories;
    private Instant generatedAt;

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(name = "DashboardUsers")
    public static class Users {
        private long total;
        private long landlords;
        private long tenants;
        private long moderators;
        private long admins;
        private long newToday;
        private long newThisWeek;
        private long newThisMonth;
        private long activeCount;
        private long pendingVerifyCount;
        private long lockedCount;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(name = "DashboardListings")
    public static class Listings {
        private long active;
        private long pending;
        private long expired;
        private long locked;
        private long draft;
        private long rejected;
        private long hidden;
        private long closed;
        private long needReview;
        private long newToday;
        private long newThisWeek;
        private long newThisMonth;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(name = "DashboardReports")
    public static class Reports {
        private long pending;
        private long reviewing;
        private long resolvedThisMonth;
        private long rejectedThisMonth;
        private long criticalPending;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(name = "DashboardRevenue")
    public static class Revenue {
        private BigDecimal today;
        private BigDecimal thisWeek;
        private BigDecimal thisMonth;
        private BigDecimal lastMonth;
        private BigDecimal growthPercent;
        private BigDecimal allTime;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(name = "DashboardPayments")
    public static class Payments {
        private long successCount;
        private long failedCount;
        private long pendingCount;
        private long refundedCount;
        private BigDecimal successRatePercent;
        private BigDecimal failureRatePercent;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(name = "DashboardAiAlerts")
    public static class AiAlerts {
        private long listingsFlaggedBySentiment;
        private long listingsWithPriceDeviation;
        private long pendingSentimentAnalysis;
        private boolean sentimentModuleEnabled;
        private boolean recommendationModuleEnabled;
        private boolean chatbotModuleEnabled;
        private boolean priceModuleEnabled;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(name = "DashboardTopProvince")
    public static class TopProvince {
        private Long provinceId;
        private String name;
        private long listingCount;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(name = "DashboardTopCategory")
    public static class TopCategory {
        private Long categoryId;
        private String code;
        private String name;
        private long listingCount;
    }
}
