package com.webtro.modules.ai.engine;

import com.webtro.common.enums.GenderRequirement;
import com.webtro.modules.ai.spi.InteractionSignalGateway.SearchSignal;
import com.webtro.modules.ai.spi.ListingDataGateway.ListingAttr;
import com.webtro.modules.ai.spi.UserDataGateway.UserPreference;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Bộ gợi ý tin đăng dựa trên nội dung (canonical mục 10.2, §9.2). Rule-based có trọng số (mục 13.2).
 *
 * <p>Hiện thực chốt: {@link ContentBasedRecommendationEngine} — dựng {@code UserPreferenceProfile}
 * 7 chiều từ hành vi, chấm điểm bằng công thức 9 số hạng (Σ trọng số = 1.00), chuẩn hóa lại khi
 * {@code genderMatch} không áp dụng (chia cho W = 0.94).
 */
public interface RecommendationEngine {

    /** Một tin trong lịch sử hành vi kèm trọng số nguồn (xem=1, tìm=2, lưu=3, liên hệ=5). */
    record WeightedListing(ListingAttr attr, int weight) {
    }

    /**
     * Hồ sơ sở thích 7 chiều (canonical mục 10.2 — phủ 1-1 với 11 mục "Dữ liệu đầu vào" §9.2).
     *
     * @param provinceWeights  trọng số chuẩn hóa [0,1] theo tỉnh
     * @param districtWeights  trọng số chuẩn hóa [0,1] theo quận/huyện
     * @param wardWeights      trọng số chuẩn hóa [0,1] theo phường/xã
     * @param categoryWeights  trọng số chuẩn hóa [0,1] theo loại tin
     * @param amenityWeights   trọng số chuẩn hóa [0,1] theo tiện ích
     * @param priceLow/priceHigh khoảng giá ưu tiên (percentile 10–90); có thể {@code null}
     * @param areaLow/areaHigh khoảng diện tích ưu tiên (percentile 10–90); có thể {@code null}
     * @param preferredOccupants số người ở ưu tiên (mode hành vi hoặc khai báo); có thể {@code null}
     * @param preferredGender  giới tính ở ghép ưu tiên (khai báo); có thể {@code null}
     * @param counts           số lượng hành vi từng nguồn (giải thích)
     * @param empty            hồ sơ rỗng (không đủ dữ liệu → cold start)
     */
    record UserPreferenceProfile(
            Map<Long, BigDecimal> provinceWeights,
            Map<Long, BigDecimal> districtWeights,
            Map<Long, BigDecimal> wardWeights,
            Map<Long, BigDecimal> categoryWeights,
            Map<Long, BigDecimal> amenityWeights,
            BigDecimal priceLow,
            BigDecimal priceHigh,
            BigDecimal areaLow,
            BigDecimal areaHigh,
            Integer preferredOccupants,
            GenderRequirement preferredGender,
            BehaviorCounts counts,
            boolean empty) {
    }

    record BehaviorCounts(int views, int searches, int favorites, int contacts) {
    }

    /**
     * Điểm thành phần từng số hạng (giải thích + tái dựng điểm tổng — §9.2). {@code genderScore} =
     * {@code null} khi không áp dụng ({@code NULL} khác {@code 0}).
     */
    record ScoreBreakdown(
            BigDecimal locationScore,
            BigDecimal areaScore,
            BigDecimal priceScore,
            BigDecimal categoryScore,
            BigDecimal amenityScore,
            BigDecimal occupantScore,
            BigDecimal genderScore,
            BigDecimal trustScoreNorm,
            BigDecimal freshnessScore,
            BigDecimal promotedBoost,
            BigDecimal appliedWeightSum,
            BigDecimal finalScore,
            List<String> matchReasons) {
    }

    record ScoredListing(ListingAttr listing, ScoreBreakdown breakdown) {
    }

    /** Dựng hồ sơ sở thích từ hành vi + tìm kiếm + khai báo. */
    UserPreferenceProfile buildProfile(List<WeightedListing> behavior, List<SearchSignal> searches,
                                       UserPreference declared);

    /**
     * Chấm điểm và xếp hạng ứng viên theo hồ sơ.
     *
     * @param profile          hồ sơ sở thích
     * @param candidates       tin ứng viên (đã lọc public + loại trừ ở tầng service)
     * @param promotedBoostCap trần {@code ai.recommendation.promoted_boost} (1.15)
     * @param size             số tin muốn lấy
     */
    List<ScoredListing> rank(UserPreferenceProfile profile, List<ListingAttr> candidates,
                             BigDecimal promotedBoostCap, int size);
}
