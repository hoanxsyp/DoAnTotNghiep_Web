package com.webtro.modules.ai.engine;

import com.webtro.common.enums.AdministrativeUnitType;
import com.webtro.common.enums.FurnitureStatus;
import com.webtro.common.enums.PriceConfidence;
import com.webtro.common.enums.ToiletType;
import com.webtro.modules.ai.spi.ListingDataGateway.ComparableListing;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Ước lượng giá theo phương pháp so sánh + hedonic (canonical mục 10.4, §9.4).
 *
 * <p>Giá cơ sở = median(giá/m²) × diện tích; điều chỉnh theo tiện nghi (nội thất đầy đủ, toilet
 * riêng, thang máy, chỗ để xe, giờ tự do, mặt tiền — hệ số cấu hình được); khoảng = percentile
 * 25/50/75 của tổng giá comparable; độ tin cậy theo số mẫu và độ phân tán IQR/median.
 * Nếu số mẫu &lt; ngưỡng → {@link PriceConfidence#INSUFFICIENT_DATA}, KHÔNG dự đoán.
 */
@Component
public class ComparableHedonicPriceEstimator implements PriceEstimator {

    // Phải <= 20 ký tự cho khớp cột prediction_histories.estimator_version VARCHAR(20).
    private static final String ESTIMATOR_VERSION = "hedonic-1.0";
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Override
    public String version() {
        return ESTIMATOR_VERSION;
    }

    @Override
    public PriceEstimate estimate(PriceInput input, List<ComparableListing> comparables,
                                  AdministrativeUnitType scopeUsed, int minSamples,
                                  HedonicWeights weights, BigDecimal deviationFlagRatio) {
        int n = comparables == null ? 0 : comparables.size();

        // Bước 2 §9.4: thiếu mẫu → INSUFFICIENT_DATA, không dự đoán.
        if (n < minSamples) {
            return new PriceEstimate(false, PriceConfidence.INSUFFICIENT_DATA, null,
                    null, null, null, null, null, n, scopeUsed, null, null, List.of(),
                    null, false,
                    "Chỉ tìm được " + n + " tin tương đương, cần tối thiểu " + minSamples + " tin");
        }

        BigDecimal area = input.area();

        // Bước 3: giá/m² của từng tin, lấy median → giá cơ sở.
        List<BigDecimal> pricePerSqmList = new ArrayList<>();
        List<BigDecimal> totalPrices = new ArrayList<>();
        for (ComparableListing c : comparables) {
            if (c.area() == null || c.area().signum() <= 0 || c.price() == null) {
                continue;
            }
            pricePerSqmList.add(c.price().divide(c.area(), 4, RoundingMode.HALF_UP));
            totalPrices.add(c.price());
        }
        pricePerSqmList.sort(BigDecimal::compareTo);
        totalPrices.sort(BigDecimal::compareTo);

        BigDecimal medianPerSqm = percentile(pricePerSqmList, 50);
        BigDecimal basePrice = medianPerSqm.multiply(area).setScale(0, RoundingMode.HALF_UP);

        // Bước 4: điều chỉnh hedonic (cộng dồn phần trăm).
        List<Adjustment> adjustments = new ArrayList<>();
        BigDecimal totalPercent = BigDecimal.ZERO;

        totalPercent = addAdj(adjustments, basePrice, totalPercent,
                input.furnitureStatus() == FurnitureStatus.FULL, "FURNITURE_FULL",
                "Nội thất đầy đủ", weights.furnitureFull());
        totalPercent = addAdj(adjustments, basePrice, totalPercent,
                input.toiletType() == ToiletType.PRIVATE, "TOILET_PRIVATE",
                "Toilet riêng", weights.toiletPrivate());
        totalPercent = addAdj(adjustments, basePrice, totalPercent,
                input.hasElevator(), "ELEVATOR", "Có thang máy", weights.elevator());
        totalPercent = addAdj(adjustments, basePrice, totalPercent,
                input.hasParking(), "PARKING", "Có chỗ để xe", weights.parking());
        totalPercent = addAdj(adjustments, basePrice, totalPercent,
                input.curfewType() == com.webtro.common.enums.CurfewType.FREE, "CURFEW_FREE",
                "Giờ giấc tự do", weights.curfewFree());
        totalPercent = addAdj(adjustments, basePrice, totalPercent,
                input.streetFront(), "STREET_FRONT", "Mặt tiền", weights.streetFront());

        BigDecimal multiplier = BigDecimal.ONE.add(totalPercent);
        BigDecimal suggested = basePrice.multiply(multiplier).setScale(0, RoundingMode.HALF_UP);

        // Bước 5: khoảng percentile 25/50/75 của tổng giá, co giãn theo cùng tỷ lệ hedonic.
        BigDecimal p25 = percentile(totalPrices, 25).multiply(multiplier).setScale(0, RoundingMode.HALF_UP);
        BigDecimal p50 = percentile(totalPrices, 50).multiply(multiplier).setScale(0, RoundingMode.HALF_UP);
        BigDecimal p75 = percentile(totalPrices, 75).multiply(multiplier).setScale(0, RoundingMode.HALF_UP);

        BigDecimal iqrRatio = dispersion(totalPrices, percentile(totalPrices, 50));
        PriceConfidence confidence = confidenceOf(n, iqrRatio, minSamples);
        BigDecimal confidenceScore = confidenceScoreOf(confidence, n, iqrRatio);

        // Bước 6: so lệch giá nhập (nếu có) — chỉ GHI CỜ, tuyệt đối không chặn.
        BigDecimal deviationRatio = null;
        boolean deviationFlagged = false;
        if (input.inputPrice() != null && input.inputPrice().signum() > 0 && suggested.signum() > 0) {
            deviationRatio = input.inputPrice().subtract(suggested)
                    .divide(suggested, 4, RoundingMode.HALF_UP);
            deviationFlagged = deviationRatio.abs().compareTo(deviationFlagRatio) > 0;
        }

        String explanation = buildExplanation(n, scopeUsed, medianPerSqm, area, adjustments);

        return new PriceEstimate(true, confidence, confidenceScore, suggested,
                p25, p50, p75, medianPerSqm, n, scopeUsed, iqrRatio, basePrice, adjustments,
                deviationRatio, deviationFlagged, explanation);
    }

    private BigDecimal addAdj(List<Adjustment> adjustments, BigDecimal base, BigDecimal accPercent,
                              boolean apply, String factor, String label, BigDecimal weight) {
        if (!apply || weight == null || weight.signum() == 0) {
            return accPercent;
        }
        BigDecimal amount = base.multiply(weight).setScale(0, RoundingMode.HALF_UP);
        adjustments.add(new Adjustment(factor, label,
                weight.multiply(HUNDRED).setScale(1, RoundingMode.HALF_UP), amount));
        return accPercent.add(weight);
    }

    // ------------------------------------------------------------------
    //  Thống kê
    // ------------------------------------------------------------------

    /** Percentile theo nội suy tuyến tính; danh sách đã sắp tăng dần. */
    private BigDecimal percentile(List<BigDecimal> sorted, int p) {
        if (sorted.isEmpty()) {
            return BigDecimal.ZERO;
        }
        if (sorted.size() == 1) {
            return sorted.get(0);
        }
        double rank = (p / 100.0) * (sorted.size() - 1);
        int lo = (int) Math.floor(rank);
        int hi = (int) Math.ceil(rank);
        BigDecimal loVal = sorted.get(lo);
        if (lo == hi) {
            return loVal;
        }
        BigDecimal hiVal = sorted.get(hi);
        BigDecimal frac = BigDecimal.valueOf(rank - lo);
        return loVal.add(hiVal.subtract(loVal).multiply(frac));
    }

    private BigDecimal dispersion(List<BigDecimal> sorted, BigDecimal median) {
        if (median.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal iqr = percentile(sorted, 75).subtract(percentile(sorted, 25));
        return iqr.divide(median, 4, RoundingMode.HALF_UP);
    }

    private PriceConfidence confidenceOf(int n, BigDecimal iqrRatio, int minSamples) {
        double iqr = iqrRatio.doubleValue();
        if (n >= 20 && iqr <= 0.25) {
            return PriceConfidence.HIGH;
        }
        if (n >= 12 && iqr <= 0.40) {
            return PriceConfidence.MEDIUM;
        }
        if (n >= minSamples) {
            return PriceConfidence.LOW;
        }
        return PriceConfidence.INSUFFICIENT_DATA;
    }

    private BigDecimal confidenceScoreOf(PriceConfidence c, int n, BigDecimal iqrRatio) {
        // Ánh xạ vào biên điểm của từng mức (canonical mục 7.4.1 bảng confidence).
        double base = switch (c) {
            case HIGH -> 0.80;
            case MEDIUM -> 0.55;
            case LOW -> 0.30;
            case INSUFFICIENT_DATA -> 0.0;
        };
        double span = switch (c) {
            case HIGH -> 0.20;
            case MEDIUM -> 0.24;
            case LOW -> 0.24;
            case INSUFFICIENT_DATA -> 0.0;
        };
        // Càng nhiều mẫu, IQR càng nhỏ → tiến gần cận trên của mức.
        double quality = Math.max(0.0, Math.min(1.0, 1.0 - iqrRatio.doubleValue()));
        double value = base + span * quality;
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private String buildExplanation(int n, AdministrativeUnitType scope, BigDecimal medianPerSqm,
                                    BigDecimal area, List<Adjustment> adjustments) {
        StringBuilder sb = new StringBuilder();
        sb.append("Giá cơ sở = trung vị ")
                .append(medianPerSqm.setScale(0, RoundingMode.HALF_UP))
                .append(" đ/m² × ").append(area.stripTrailingZeros().toPlainString())
                .append(" m², tính từ ").append(n).append(" tin tương đương ")
                .append(scopeLabel(scope)).append(".");
        if (!adjustments.isEmpty()) {
            sb.append(" Điều chỉnh: ");
            List<String> parts = new ArrayList<>();
            for (Adjustment a : adjustments) {
                parts.add(a.label() + " " + (a.percent().signum() >= 0 ? "+" : "")
                        + a.percent().stripTrailingZeros().toPlainString() + "%");
            }
            sb.append(String.join(", ", parts)).append(".");
        }
        String out = sb.toString();
        return out.length() > 500 ? out.substring(0, 500) : out;
    }

    private String scopeLabel(AdministrativeUnitType scope) {
        return switch (scope) {
            case WARD -> "trong phường/xã";
            case DISTRICT -> "trong quận/huyện";
            case PROVINCE -> "trong tỉnh/thành";
        };
    }
}
