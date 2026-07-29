package com.webtro.modules.ai.engine;

import com.webtro.common.enums.CategoryCode;
import com.webtro.common.enums.GenderRequirement;
import com.webtro.modules.ai.spi.InteractionSignalGateway.SearchSignal;
import com.webtro.modules.ai.spi.ListingDataGateway.ListingAttr;
import com.webtro.modules.ai.spi.UserDataGateway.UserPreference;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gợi ý dựa trên nội dung với công thức 9 số hạng (canonical mục 10.2, §9.2).
 *
 * <pre>
 * score = 0.22·location + 0.12·area + 0.20·price + 0.12·category + 0.08·amenity
 *       + 0.08·occupant + 0.06·gender + 0.06·trustNorm + 0.06·freshness
 * score = score × promotedBoost            (trần 1.15)
 * </pre>
 *
 * <p>{@code genderMatch} chỉ áp dụng cho tin {@code ROOMMATE}; với tin khác, bỏ số hạng đó ra và
 * chia lại cho tổng trọng số thực áp dụng (W = 0.94) — không gán mặc định 1.0 để tránh thiên vị.
 */
@Component
public class ContentBasedRecommendationEngine implements RecommendationEngine {

    // Trọng số 9 số hạng (Σ = 1.00).
    private static final BigDecimal W_LOCATION = new BigDecimal("0.22");
    private static final BigDecimal W_AREA = new BigDecimal("0.12");
    private static final BigDecimal W_PRICE = new BigDecimal("0.20");
    private static final BigDecimal W_CATEGORY = new BigDecimal("0.12");
    private static final BigDecimal W_AMENITY = new BigDecimal("0.08");
    private static final BigDecimal W_OCCUPANT = new BigDecimal("0.08");
    private static final BigDecimal W_GENDER = new BigDecimal("0.06");
    private static final BigDecimal W_TRUST = new BigDecimal("0.06");
    private static final BigDecimal W_FRESHNESS = new BigDecimal("0.06");

    private static final BigDecimal NEUTRAL = new BigDecimal("0.50");
    private static final long FRESHNESS_HORIZON_DAYS = 30;

    @Override
    public UserPreferenceProfile buildProfile(List<WeightedListing> behavior, List<SearchSignal> searches,
                                              UserPreference declared) {
        behavior = behavior == null ? List.of() : behavior;
        searches = searches == null ? List.of() : searches;

        Map<Long, BigDecimal> provinces = new HashMap<>();
        Map<Long, BigDecimal> districts = new HashMap<>();
        Map<Long, BigDecimal> wards = new HashMap<>();
        Map<Long, BigDecimal> categories = new HashMap<>();
        Map<Long, BigDecimal> amenities = new HashMap<>();
        List<Double> prices = new ArrayList<>();
        List<Double> areas = new ArrayList<>();
        Map<Integer, Integer> occupantFreq = new HashMap<>();

        for (WeightedListing wl : behavior) {
            ListingAttr a = wl.attr();
            BigDecimal w = BigDecimal.valueOf(wl.weight());
            accumulate(provinces, a.provinceId(), w);
            accumulate(districts, a.districtId(), w);
            accumulate(wards, a.wardId(), w);
            accumulate(categories, a.categoryId(), w);
            if (a.amenityIds() != null) {
                for (Long am : a.amenityIds()) {
                    accumulate(amenities, am, w);
                }
            }
            if (a.price() != null) {
                for (int i = 0; i < wl.weight(); i++) {
                    prices.add(a.price().doubleValue());
                }
            }
            if (a.area() != null) {
                for (int i = 0; i < wl.weight(); i++) {
                    areas.add(a.area().doubleValue());
                }
            }
            if (a.maxOccupants() != null) {
                occupantFreq.merge(a.maxOccupants(), wl.weight(), Integer::sum);
            }
        }

        // Tìm kiếm (w=2): khu vực + loại + biên giá/diện tích.
        for (SearchSignal s : searches) {
            BigDecimal w = BigDecimal.valueOf(2);
            accumulate(provinces, s.provinceId(), w);
            accumulate(districts, s.districtId(), w);
            accumulate(wards, s.wardId(), w);
            accumulate(categories, s.categoryId(), w);
            if (s.priceFrom() != null) {
                prices.add(s.priceFrom().doubleValue());
            }
            if (s.priceTo() != null) {
                prices.add(s.priceTo().doubleValue());
            }
            if (s.areaFrom() != null) {
                areas.add(s.areaFrom().doubleValue());
            }
            if (s.areaTo() != null) {
                areas.add(s.areaTo().doubleValue());
            }
        }

        normalize(provinces);
        normalize(districts);
        normalize(wards);
        normalize(categories);
        normalize(amenities);

        Integer preferredOccupants = mode(occupantFreq);
        GenderRequirement preferredGender = null;
        if (declared != null) {
            if (declared.preferredOccupants() != null) {
                preferredOccupants = declared.preferredOccupants();
            }
            preferredGender = declared.preferredGenderRequirement();
        }

        int views = 0;
        int favorites = 0;
        int contacts = 0;
        for (WeightedListing wl : behavior) {
            switch (wl.weight()) {
                case 1 -> views++;
                case 3 -> favorites++;
                case 5 -> contacts++;
                default -> { /* nguồn khác */ }
            }
        }

        boolean empty = behavior.isEmpty() && searches.isEmpty();

        return new UserPreferenceProfile(provinces, districts, wards, categories, amenities,
                percentile(prices, 10), percentile(prices, 90),
                percentile(areas, 10), percentile(areas, 90),
                preferredOccupants, preferredGender,
                new BehaviorCounts(views, searches.size(), favorites, contacts), empty);
    }

    @Override
    public List<ScoredListing> rank(UserPreferenceProfile profile, List<ListingAttr> candidates,
                                    BigDecimal promotedBoostCap, int size) {
        List<ScoredListing> scored = new ArrayList<>();
        for (ListingAttr c : candidates) {
            scored.add(score(profile, c, promotedBoostCap));
        }
        scored.sort(Comparator.comparing(
                (ScoredListing s) -> s.breakdown().finalScore()).reversed());
        return scored.size() > size ? scored.subList(0, size) : scored;
    }

    private ScoredListing score(UserPreferenceProfile p, ListingAttr c, BigDecimal boostCap) {
        BigDecimal location = locationScore(p, c);
        BigDecimal area = rangeScore(c.area(), p.areaLow(), p.areaHigh());
        BigDecimal price = rangeScore(c.price(), p.priceLow(), p.priceHigh());
        BigDecimal category = shareScore(p.categoryWeights(), c.categoryId());
        BigDecimal amenity = amenityScore(p, c);
        BigDecimal occupant = occupantScore(p, c);
        boolean genderApplies = c.categoryCode() == CategoryCode.ROOMMATE;
        BigDecimal gender = genderApplies ? genderScore(p, c) : null;
        BigDecimal trust = BigDecimal.valueOf(c.trustScore()).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        BigDecimal freshness = freshnessScore(c.publishedAt());

        BigDecimal weighted = W_LOCATION.multiply(location)
                .add(W_AREA.multiply(area))
                .add(W_PRICE.multiply(price))
                .add(W_CATEGORY.multiply(category))
                .add(W_AMENITY.multiply(amenity))
                .add(W_OCCUPANT.multiply(occupant))
                .add(W_TRUST.multiply(trust))
                .add(W_FRESHNESS.multiply(freshness));

        BigDecimal appliedWeightSum = W_LOCATION.add(W_AREA).add(W_PRICE).add(W_CATEGORY)
                .add(W_AMENITY).add(W_OCCUPANT).add(W_TRUST).add(W_FRESHNESS);

        if (genderApplies) {
            weighted = weighted.add(W_GENDER.multiply(gender));
            appliedWeightSum = appliedWeightSum.add(W_GENDER);
        }

        // Chuẩn hóa lại theo tổng trọng số thực áp dụng (W = 0.94 khi bỏ gender).
        BigDecimal base = weighted.divide(appliedWeightSum, 6, RoundingMode.HALF_UP);

        BigDecimal boost = c.promoted() ? boostCap : BigDecimal.ONE;
        BigDecimal finalScore = base.multiply(boost).setScale(4, RoundingMode.HALF_UP);
        if (finalScore.compareTo(BigDecimal.ONE) > 0) {
            finalScore = BigDecimal.ONE;
        }

        List<String> reasons = buildReasons(p, c, location, price, category, amenity);

        ScoreBreakdown breakdown = new ScoreBreakdown(
                scale4(location), scale4(area), scale4(price), scale4(category), scale4(amenity),
                scale4(occupant), gender == null ? null : scale4(gender), scale4(trust),
                scale4(freshness), boost.setScale(3, RoundingMode.HALF_UP),
                appliedWeightSum.setScale(2, RoundingMode.HALF_UP), finalScore, reasons);
        return new ScoredListing(c, breakdown);
    }

    // ------------------------------------------------------------------
    //  Từng số hạng
    // ------------------------------------------------------------------

    private BigDecimal locationScore(UserPreferenceProfile p, ListingAttr c) {
        double ward = weightOf(p.wardWeights(), c.wardId());
        double district = weightOf(p.districtWeights(), c.districtId());
        double province = weightOf(p.provinceWeights(), c.provinceId());
        double blended = ward * 1.0 + district * 0.6 + province * 0.3;
        return clamp01(blended);
    }

    private BigDecimal shareScore(Map<Long, BigDecimal> weights, Long key) {
        if (weights.isEmpty()) {
            return NEUTRAL;
        }
        double v = weightOf(weights, key);
        double max = weights.values().stream().mapToDouble(BigDecimal::doubleValue).max().orElse(1.0);
        return max <= 0 ? NEUTRAL : clamp01(v / max);
    }

    private BigDecimal amenityScore(UserPreferenceProfile p, ListingAttr c) {
        if (p.amenityWeights().isEmpty()) {
            return NEUTRAL;
        }
        if (c.amenityIds() == null || c.amenityIds().isEmpty()) {
            return BigDecimal.ZERO;
        }
        double matched = 0;
        double total = p.amenityWeights().values().stream().mapToDouble(BigDecimal::doubleValue).sum();
        for (Long am : c.amenityIds()) {
            BigDecimal w = p.amenityWeights().get(am);
            if (w != null) {
                matched += w.doubleValue();
            }
        }
        return total <= 0 ? NEUTRAL : clamp01(matched / total);
    }

    private BigDecimal occupantScore(UserPreferenceProfile p, ListingAttr c) {
        if (p.preferredOccupants() == null || c.maxOccupants() == null) {
            return NEUTRAL;
        }
        int want = p.preferredOccupants();
        int cap = c.maxOccupants();
        if (cap >= want) {
            // Đủ chỗ; càng sát nhu cầu càng tốt (không lãng phí).
            double closeness = 1.0 - Math.min(1.0, (cap - want) / (double) Math.max(1, want));
            return clamp01(0.6 + 0.4 * closeness);
        }
        return BigDecimal.ZERO; // không đủ chỗ ở
    }

    private BigDecimal genderScore(UserPreferenceProfile p, ListingAttr c) {
        GenderRequirement want = p.preferredGender();
        GenderRequirement have = c.genderRequirement();
        if (want == null || have == null) {
            return NEUTRAL;
        }
        if (have == GenderRequirement.ANY || have == want) {
            return BigDecimal.ONE;
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal rangeScore(BigDecimal value, BigDecimal low, BigDecimal high) {
        if (value == null || low == null || high == null) {
            return NEUTRAL;
        }
        double v = value.doubleValue();
        double lo = low.doubleValue();
        double hi = high.doubleValue();
        if (hi < lo) {
            double t = lo;
            lo = hi;
            hi = t;
        }
        if (v >= lo && v <= hi) {
            return BigDecimal.ONE;
        }
        double mid = (lo + hi) / 2.0;
        double halfSpan = Math.max(1.0, (hi - lo) / 2.0);
        double dist = Math.abs(v - mid) - halfSpan;
        double score = Math.max(0.0, 1.0 - dist / (halfSpan * 2.0));
        return clamp01(score);
    }

    private BigDecimal freshnessScore(Instant publishedAt) {
        if (publishedAt == null) {
            return NEUTRAL;
        }
        long days = Duration.between(publishedAt, Instant.now()).toDays();
        if (days <= 0) {
            return BigDecimal.ONE;
        }
        if (days >= FRESHNESS_HORIZON_DAYS) {
            return BigDecimal.ZERO;
        }
        return clamp01(1.0 - (double) days / FRESHNESS_HORIZON_DAYS);
    }

    private List<String> buildReasons(UserPreferenceProfile p, ListingAttr c,
                                      BigDecimal location, BigDecimal price,
                                      BigDecimal category, BigDecimal amenity) {
        List<String> reasons = new ArrayList<>();
        if (location.compareTo(new BigDecimal("0.5")) >= 0 && c.shortAddress() != null) {
            reasons.add("Khu vực bạn thường xem (" + c.shortAddress() + ")");
        }
        if (price.compareTo(new BigDecimal("0.8")) >= 0 && p.priceLow() != null && p.priceHigh() != null) {
            reasons.add("Trong khoảng giá bạn quan tâm");
        }
        if (category.compareTo(new BigDecimal("0.6")) >= 0 && c.categoryName() != null) {
            reasons.add("Cùng loại tin bạn hay xem (" + c.categoryName() + ")");
        }
        if (amenity.compareTo(new BigDecimal("0.5")) >= 0 && !p.amenityWeights().isEmpty()) {
            reasons.add("Có tiện ích như tin bạn đã quan tâm");
        }
        if (reasons.isEmpty()) {
            reasons.add("Phù hợp với nhu cầu của bạn");
        }
        return reasons;
    }

    // ------------------------------------------------------------------
    //  Tiện ích
    // ------------------------------------------------------------------

    private void accumulate(Map<Long, BigDecimal> map, Long key, BigDecimal w) {
        if (key == null) {
            return;
        }
        map.merge(key, w, BigDecimal::add);
    }

    private void normalize(Map<Long, BigDecimal> map) {
        BigDecimal total = map.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) {
            return;
        }
        for (Map.Entry<Long, BigDecimal> e : new LinkedHashMap<>(map).entrySet()) {
            map.put(e.getKey(), e.getValue().divide(total, 6, RoundingMode.HALF_UP));
        }
    }

    private double weightOf(Map<Long, BigDecimal> map, Long key) {
        BigDecimal v = key == null ? null : map.get(key);
        return v == null ? 0.0 : v.doubleValue();
    }

    private Integer mode(Map<Integer, Integer> freq) {
        return freq.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private BigDecimal percentile(List<Double> values, int p) {
        if (values.isEmpty()) {
            return null;
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);
        double rank = (p / 100.0) * (sorted.size() - 1);
        int lo = (int) Math.floor(rank);
        int hi = (int) Math.ceil(rank);
        double val = lo == hi ? sorted.get(lo)
                : sorted.get(lo) + (sorted.get(hi) - sorted.get(lo)) * (rank - lo);
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal clamp01(double v) {
        if (v < 0) {
            return BigDecimal.ZERO;
        }
        if (v > 1) {
            return BigDecimal.ONE;
        }
        return BigDecimal.valueOf(v);
    }

    private BigDecimal scale4(BigDecimal v) {
        return v.setScale(4, RoundingMode.HALF_UP);
    }
}
