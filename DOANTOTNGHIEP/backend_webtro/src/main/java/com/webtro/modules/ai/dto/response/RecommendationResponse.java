package com.webtro.modules.ai.dto.response;

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
 * Kết quả gợi ý tin đăng (docs/03 mục 7.2, §9.2). {@code scoreBreakdown} + {@code matchReasons} làm
 * AI <b>giải thích được</b> điểm tổng (yêu cầu chất lượng §9.2).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "RecommendationResponse", description = "Danh sách tin gợi ý")
public class RecommendationResponse {

    private String source;
    private Boolean personalized;
    private Boolean coldStart;
    private List<String> coldStartStrategy;
    private String coldStartNote;
    private Long recommendationLogId;
    private String batchId;
    private ProfileSummary profileSummary;
    private List<RecommendationItem> items;
    private Instant generatedAt;
    private Boolean cacheHit;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendationItem {
        private Long id;
        private String slug;
        private String title;
        private String categoryCode;
        private String categoryName;
        private BigDecimal price;
        private BigDecimal area;
        private String shortAddress;
        private String thumbnailUrl;
        private Integer trustScore;
        private BigDecimal averageRating;
        private Boolean promoted;
        private BigDecimal matchScore;
        private ScoreBreakdown scoreBreakdown;
        private List<String> matchReasons;
    }

    /** Điểm thành phần từng số hạng; {@code genderMatch} = {@code null} khi không áp dụng. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreBreakdown {
        private BigDecimal locationMatch;
        private BigDecimal areaMatch;
        private BigDecimal priceMatch;
        private BigDecimal categoryMatch;
        private BigDecimal amenityMatch;
        private BigDecimal occupantMatch;
        private BigDecimal genderMatch;
        private BigDecimal trustScoreNorm;
        private BigDecimal freshness;
        private BigDecimal promotedBoost;
        private BigDecimal appliedWeightSum;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProfileSummary {
        private BigDecimal preferredPriceLow;
        private BigDecimal preferredPriceHigh;
        private BigDecimal preferredAreaLow;
        private BigDecimal preferredAreaHigh;
        private Integer preferredOccupants;
        private BehaviorCounts behaviorCounts;
        private String note;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BehaviorCounts {
        private Integer views;
        private Integer searches;
        private Integer favorites;
        private Integer contacts;
    }
}
