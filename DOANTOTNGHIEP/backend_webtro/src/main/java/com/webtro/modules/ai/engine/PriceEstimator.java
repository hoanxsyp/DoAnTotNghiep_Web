package com.webtro.modules.ai.engine;

import com.webtro.common.enums.AdministrativeUnitType;
import com.webtro.common.enums.CurfewType;
import com.webtro.common.enums.FurnitureStatus;
import com.webtro.common.enums.PriceConfidence;
import com.webtro.common.enums.ToiletType;
import com.webtro.modules.ai.spi.ListingDataGateway.ComparableListing;

import java.math.BigDecimal;
import java.util.List;

/**
 * Bộ ước lượng giá thuê (canonical mục 10.4, §9.4). Đặt sau interface để thay implementation.
 *
 * <p>Hiện thực chốt: {@link ComparableHedonicPriceEstimator} — so sánh tin tương đương + điều chỉnh
 * hedonic theo tiện nghi. Toàn bộ hệ số/ngưỡng đọc từ {@code system_configs} (không hardcode).
 */
public interface PriceEstimator {

    String version();

    /** Tham số đầu vào ước lượng (đã chuẩn hóa từ request). */
    record PriceInput(
            Long categoryId,
            Long provinceId,
            Long districtId,
            Long wardId,
            BigDecimal area,
            Integer roomCount,
            Integer toiletCount,
            FurnitureStatus furnitureStatus,
            ToiletType toiletType,
            List<Long> amenityIds,
            boolean streetFront,
            boolean hasElevator,
            boolean hasParking,
            CurfewType curfewType,
            BigDecimal inputPrice) {
    }

    /** Hệ số hedonic (đọc từ config), để service truyền vào engine (không hardcode trong engine). */
    record HedonicWeights(
            BigDecimal furnitureFull,
            BigDecimal toiletPrivate,
            BigDecimal elevator,
            BigDecimal parking,
            BigDecimal curfewFree,
            BigDecimal streetFront) {
    }

    /** Một hệ số điều chỉnh đã áp dụng (giải thích). */
    record Adjustment(String factor, String label, BigDecimal percent, BigDecimal amount) {
    }

    /**
     * Kết quả ước lượng.
     *
     * @param available       có dự đoán được không ({@code false} khi {@code INSUFFICIENT_DATA})
     * @param confidence      mức tin cậy
     * @param confidenceScore điểm tin cậy [0, 1]
     * @param suggestedPrice  giá đề xuất (median điều chỉnh)
     * @param priceLow/Median/High khoảng percentile 25/50/75
     * @param pricePerSqm     median giá/m²
     * @param sampleSize      số mẫu dùng
     * @param scopeUsed       phạm vi địa lý thực dùng
     * @param dispersionRatio IQR/median (đo phân tán)
     * @param basePrice       giá cơ sở trước điều chỉnh hedonic
     * @param adjustments     danh sách hệ số hedonic đã áp dụng
     * @param deviationRatio  (inputPrice − suggested)/suggested; {@code null} nếu không nhập giá
     * @param deviationFlagged lệch vượt ngưỡng {@code ai.price.deviation_flag_ratio}
     * @param explanation     giải thích tiếng Việt (≤ 500 ký tự)
     */
    record PriceEstimate(
            boolean available,
            PriceConfidence confidence,
            BigDecimal confidenceScore,
            BigDecimal suggestedPrice,
            BigDecimal priceLow,
            BigDecimal priceMedian,
            BigDecimal priceHigh,
            BigDecimal pricePerSqm,
            int sampleSize,
            AdministrativeUnitType scopeUsed,
            BigDecimal dispersionRatio,
            BigDecimal basePrice,
            List<Adjustment> adjustments,
            BigDecimal deviationRatio,
            boolean deviationFlagged,
            String explanation) {
    }

    /**
     * Ước lượng từ các tin so sánh đã lọc theo phạm vi.
     *
     * @param input          tham số đầu vào
     * @param comparables    tin so sánh (cùng category, diện tích ±tolerance, trong cửa sổ)
     * @param scopeUsed      phạm vi địa lý của tập {@code comparables}
     * @param minSamples     {@code ai.price.min_samples}; dưới ngưỡng → INSUFFICIENT_DATA
     * @param weights        hệ số hedonic
     * @param deviationFlagRatio {@code ai.price.deviation_flag_ratio}
     */
    PriceEstimate estimate(PriceInput input, List<ComparableListing> comparables,
                           AdministrativeUnitType scopeUsed, int minSamples,
                           HedonicWeights weights, BigDecimal deviationFlagRatio);
}
