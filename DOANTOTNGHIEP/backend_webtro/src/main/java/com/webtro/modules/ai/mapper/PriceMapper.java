package com.webtro.modules.ai.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webtro.common.enums.AdministrativeUnitType;
import com.webtro.common.enums.PriceConfidence;
import com.webtro.modules.ai.dto.response.PricePredictionHistoryResponse;
import com.webtro.modules.ai.dto.response.PricePredictionResponse;
import com.webtro.modules.ai.engine.PriceEstimator.Adjustment;
import com.webtro.modules.ai.engine.PriceEstimator.PriceEstimate;
import com.webtro.modules.ai.engine.PriceEstimator.PriceInput;
import com.webtro.modules.ai.entity.PredictionHistory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Chuyển đổi kết quả engine ước lượng giá ↔ entity/dto (thủ công, Builder — canonical luật 3).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PriceMapper {

    private final ObjectMapper objectMapper;

    private static final String DISCLAIMER =
            "Giá đề xuất chỉ mang tính tham khảo dựa trên các tin đăng tương đương, không phải định "
                    + "giá chính thức. Bạn hoàn toàn có thể đặt giá khác.";

    /** Dựng entity lưu mọi lần dự đoán (kể cả INSUFFICIENT_DATA để phục vụ báo cáo chất lượng AI). */
    public PredictionHistory toEntity(PriceInput input, PriceEstimate est, Long userId,
                                      Long listingId, String estimatorVersion) {
        return PredictionHistory.builder()
                .listingId(listingId)
                .userId(userId)
                .categoryId(input.categoryId())
                .provinceId(input.provinceId())
                .districtId(input.districtId())
                .wardId(input.wardId())
                .area(input.area())
                .roomCount(input.roomCount())
                .toiletCount(input.toiletCount())
                .furnitureStatus(input.furnitureStatus())
                .amenityIds(toJson(input.amenityIds()))
                .suggestedPrice(est.suggestedPrice())
                .priceLow(est.priceLow())
                .priceMedian(est.priceMedian())
                .priceHigh(est.priceHigh())
                .pricePerSqm(est.pricePerSqm())
                .sampleSize(est.sampleSize())
                .scopeUsed(est.scopeUsed())
                .confidence(est.confidence())
                .dispersionRatio(est.dispersionRatio())
                .adjustmentDetail(adjustmentsJson(est.adjustments()))
                .explanation(truncate(est.explanation(), 500))
                .inputPrice(input.inputPrice())
                .deviationRatio(est.deviationRatio())
                .isFlagged(est.deviationFlagged())
                .isApplied(false)
                .estimatorVersion(estimatorVersion)
                .build();
    }

    public PricePredictionResponse toResponse(PredictionHistory saved, PriceEstimate est,
                                              boolean scopeExpanded, int periodDays) {
        PricePredictionResponse.PriceRange range = PricePredictionResponse.PriceRange.builder()
                .low(est.priceLow())
                .medium(est.priceMedian())
                .high(est.priceHigh())
                .build();

        List<PricePredictionResponse.Adjustment> adjustments = new ArrayList<>();
        List<String> vi = new ArrayList<>();
        for (Adjustment a : est.adjustments()) {
            adjustments.add(PricePredictionResponse.Adjustment.builder()
                    .factor(a.factor()).label(a.label()).percent(a.percent()).amount(a.amount())
                    .build());
            vi.add((a.percent().signum() >= 0 ? "Giá cao hơn do " : "Giá thấp hơn do ") + a.label().toLowerCase());
        }
        BigDecimal totalPercent = est.adjustments().stream()
                .map(Adjustment::percent).reduce(BigDecimal.ZERO, BigDecimal::add);

        PricePredictionResponse.Explanation explanation = PricePredictionResponse.Explanation.builder()
                .summary(est.explanation())
                .basePrice(est.basePrice())
                .adjustments(adjustments)
                .totalAdjustmentPercent(totalPercent)
                .factorsInVietnamese(vi)
                .build();

        PricePredictionResponse.Comparable comparable = PricePredictionResponse.Comparable.builder()
                .sampleSize(est.sampleSize())
                .scope(est.scopeUsed() == null ? null : est.scopeUsed().name())
                .scopeLabel(scopeLabel(est.scopeUsed()))
                .scopeExpanded(scopeExpanded)
                .periodDays(periodDays)
                .medianPricePerSqm(est.pricePerSqm())
                .iqrRatio(est.dispersionRatio())
                .build();

        PricePredictionResponse.Comparison comparison = buildComparison(est);

        return PricePredictionResponse.builder()
                .available(true)
                .predictionHistoryId(saved == null ? null : saved.getId())
                .suggestedPrice(est.suggestedPrice())
                .priceRange(range)
                .confidence(est.confidence())
                .confidenceScore(est.confidenceScore())
                .confidenceLabel(confidenceLabel(est.confidence()))
                .explanation(explanation)
                .comparable(comparable)
                .comparison(comparison)
                .disclaimer(DISCLAIMER)
                .predictedAt(saved == null ? java.time.Instant.now() : saved.getCreatedAt())
                .build();
    }

    private PricePredictionResponse.Comparison buildComparison(PriceEstimate est) {
        if (est.deviationRatio() == null) {
            return null;
        }
        BigDecimal ratio = est.deviationRatio();
        BigDecimal suggested = est.suggestedPrice();
        BigDecimal input = suggested.multiply(BigDecimal.ONE.add(ratio)).setScale(0, RoundingMode.HALF_UP);
        String verdict = verdictOf(ratio);
        return PricePredictionResponse.Comparison.builder()
                .inputPrice(input)
                .suggestedPrice(suggested)
                .difference(input.subtract(suggested))
                .deviationRatio(ratio)
                .deviationPercent(ratio.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP))
                .deviationFlagged(est.deviationFlagged())
                .thresholdRatio(null)
                .verdict(verdict)
                .verdictMessage(verdictMessage(verdict, ratio))
                .blocksPosting(false)
                .build();
    }

    public PricePredictionHistoryResponse toHistoryResponse(PredictionHistory h) {
        return PricePredictionHistoryResponse.builder()
                .id(h.getId())
                .listingId(h.getListingId())
                .suggestedPrice(h.getSuggestedPrice())
                .priceRange(PricePredictionResponse.PriceRange.builder()
                        .low(h.getPriceLow()).medium(h.getPriceMedian()).high(h.getPriceHigh()).build())
                .confidence(h.getConfidence())
                .sampleSize(h.getSampleSize())
                .comparableScope(h.getScopeUsed())
                .inputPrice(h.getInputPrice())
                .deviationRatio(h.getDeviationRatio())
                .deviationFlagged(h.getIsFlagged())
                .applied(h.getIsApplied())
                .createdAt(h.getCreatedAt())
                .build();
    }

    // ------------------------------------------------------------------

    private String verdictOf(BigDecimal ratio) {
        double r = ratio.doubleValue();
        if (r < -0.35) {
            return "MUCH_LOWER";
        }
        if (r < -0.15) {
            return "LOWER";
        }
        if (r <= 0.15) {
            return "REASONABLE";
        }
        if (r <= 0.35) {
            return "HIGHER";
        }
        return "MUCH_HIGHER";
    }

    private String verdictMessage(String verdict, BigDecimal ratio) {
        int pct = ratio.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP).intValue();
        return switch (verdict) {
            case "MUCH_LOWER" -> "Giá bạn nhập thấp hơn " + Math.abs(pct)
                    + "% so với giá đề xuất — thấp bất thường, có thể bị kiểm duyệt viên xem qua.";
            case "LOWER" -> "Giá bạn nhập thấp hơn " + Math.abs(pct) + "% so với thị trường.";
            case "REASONABLE" -> "Giá bạn nhập " + (pct >= 0 ? "cao hơn " + pct : "thấp hơn " + Math.abs(pct))
                    + "% so với giá đề xuất — nằm trong khoảng bình thường của thị trường.";
            case "HIGHER" -> "Giá bạn nhập cao hơn " + pct + "% so với thị trường.";
            case "MUCH_HIGHER" -> "Giá bạn nhập cao hơn " + pct + "% so với giá đề xuất. Bạn vẫn có thể "
                    + "đăng tin với giá này, nhưng tin có thể ít người liên hệ hơn và sẽ được kiểm duyệt "
                    + "viên xem qua.";
            default -> "";
        };
    }

    private String confidenceLabel(PriceConfidence c) {
        return switch (c) {
            case HIGH -> "Độ tin cậy cao";
            case MEDIUM -> "Độ tin cậy trung bình";
            case LOW -> "Độ tin cậy thấp";
            case INSUFFICIENT_DATA -> "Không đủ dữ liệu";
        };
    }

    private String scopeLabel(AdministrativeUnitType scope) {
        if (scope == null) {
            return null;
        }
        return switch (scope) {
            case WARD -> "Phường/xã";
            case DISTRICT -> "Quận/huyện";
            case PROVINCE -> "Tỉnh/thành";
        };
    }

    private String toJson(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String adjustmentsJson(List<Adjustment> adjustments) {
        try {
            List<Object> list = new ArrayList<>();
            for (Adjustment a : adjustments) {
                list.add(java.util.Map.of("factor", a.factor(), "percent", a.percent(), "amount", a.amount()));
            }
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            log.warn("Không serialize được adjustment: {}", e.getMessage());
            return "[]";
        }
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
