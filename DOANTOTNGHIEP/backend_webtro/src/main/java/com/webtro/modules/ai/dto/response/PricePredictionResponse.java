package com.webtro.modules.ai.dto.response;

import com.webtro.common.enums.PriceConfidence;
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
 * Kết quả dự đoán giá thuê (docs/03 mục 7.4.1, §9.4). {@code blocksPosting} LUÔN {@code false};
 * {@code disclaimer} LUÔN có mặt (không hiển thị AI như nguồn đảm bảo tuyệt đối §9.4).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "PricePredictionResponse", description = "Kết quả dự đoán giá thuê")
public class PricePredictionResponse {

    private Boolean available;
    private Long predictionHistoryId;

    private BigDecimal suggestedPrice;
    private PriceRange priceRange;

    private PriceConfidence confidence;
    private BigDecimal confidenceScore;
    private String confidenceLabel;

    private Explanation explanation;
    private Comparable comparable;
    private Comparison comparison;

    private String disclaimer;
    private Instant predictedAt;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceRange {
        private BigDecimal low;
        private BigDecimal medium;
        private BigDecimal high;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Explanation {
        private String summary;
        private BigDecimal basePrice;
        private List<Adjustment> adjustments;
        private BigDecimal totalAdjustmentPercent;
        private List<String> factorsInVietnamese;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Adjustment {
        private String factor;
        private String label;
        private BigDecimal percent;
        private BigDecimal amount;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Comparable {
        private Integer sampleSize;
        private String scope;
        private String scopeLabel;
        private Boolean scopeExpanded;
        private Integer periodDays;
        private BigDecimal medianPricePerSqm;
        private BigDecimal iqrRatio;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Comparison {
        private BigDecimal inputPrice;
        private BigDecimal suggestedPrice;
        private BigDecimal difference;
        private BigDecimal deviationRatio;
        private BigDecimal deviationPercent;
        private Boolean deviationFlagged;
        private BigDecimal thresholdRatio;
        private String verdict;
        private String verdictMessage;
        @Builder.Default
        private Boolean blocksPosting = false;
    }
}
