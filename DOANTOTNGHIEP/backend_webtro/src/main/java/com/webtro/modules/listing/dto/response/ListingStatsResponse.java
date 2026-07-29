package com.webtro.modules.listing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Thống kê tin đăng cho chủ trọ (docs/03 mục 4.4.20).
 *
 * <p>Khối {@code summary}, {@code sentimentSummary}, {@code promotion} tính trực tiếp từ các cột
 * đếm sẵn trên tin. {@code timeSeries}/{@code comparison} cần dữ liệu tổng hợp chéo module
 * (lịch sử xem, trung bình khu vực) nên có thể rỗng khi module đó chưa cung cấp API.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListingStatsResponse {

    private Long listingId;
    private String title;
    private String status;
    private Summary summary;
    private SentimentSummary sentimentSummary;
    private Promotion promotion;
    private List<TimeSeriesPoint> timeSeries;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private Integer viewCount;
        private Integer favoriteCount;
        private Integer contactCount;
        private Integer commentCount;
        private Integer reviewCount;
        private BigDecimal averageRating;
        private Integer trustScore;
        private BigDecimal conversionRatePercent;
        private Long daysActive;
        private Long daysRemaining;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SentimentSummary {
        private Integer positive;
        private Integer neutral;
        private Integer negative;
        private BigDecimal negativeRatio;
        private BigDecimal needReviewThresholdRatio;
        private Boolean flagged;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Promotion {
        private Boolean promoted;
        private Integer priority;
        private Instant endAt;
        private Long daysRemaining;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeSeriesPoint {
        private String date;
        private Integer views;
        private Integer favorites;
        private Integer contacts;
    }
}
