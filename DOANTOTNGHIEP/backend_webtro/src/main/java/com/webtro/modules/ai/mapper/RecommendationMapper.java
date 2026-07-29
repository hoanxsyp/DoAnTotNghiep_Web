package com.webtro.modules.ai.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webtro.common.enums.RecommendationSource;
import com.webtro.modules.ai.dto.response.RecommendationResponse;
import com.webtro.modules.ai.engine.RecommendationEngine.ScoreBreakdown;
import com.webtro.modules.ai.engine.RecommendationEngine.ScoredListing;
import com.webtro.modules.ai.engine.RecommendationEngine.UserPreferenceProfile;
import com.webtro.modules.ai.entity.RecommendationLog;
import com.webtro.modules.ai.spi.ListingDataGateway.ListingAttr;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Chuyển đổi kết quả engine gợi ý ↔ dto/entity (thủ công, Builder — canonical luật 3).
 *
 * <p>Các số hạng không có cột riêng trong {@code recommendation_logs} (location/occupant/gender +
 * {@code appliedWeightSum}) được lưu vào cột {@code context} (JSON) để tái dựng được điểm tổng
 * (§9.2 "giải thích và đánh giá hiệu quả").
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationMapper {

    private final ObjectMapper objectMapper;

    public RecommendationResponse.RecommendationItem toItem(ScoredListing scored) {
        ListingAttr a = scored.listing();
        ScoreBreakdown b = scored.breakdown();
        return RecommendationResponse.RecommendationItem.builder()
                .id(a.id())
                .slug(a.slug())
                .title(a.title())
                .categoryCode(a.categoryCode() == null ? null : a.categoryCode().name())
                .categoryName(a.categoryName())
                .price(a.price())
                .area(a.area())
                .shortAddress(a.shortAddress())
                .thumbnailUrl(a.thumbnailUrl())
                .trustScore(a.trustScore())
                .averageRating(a.averageRating())
                .promoted(a.promoted())
                .matchScore(b.finalScore())
                .scoreBreakdown(RecommendationResponse.ScoreBreakdown.builder()
                        .locationMatch(b.locationScore())
                        .areaMatch(b.areaScore())
                        .priceMatch(b.priceScore())
                        .categoryMatch(b.categoryScore())
                        .amenityMatch(b.amenityScore())
                        .occupantMatch(b.occupantScore())
                        .genderMatch(b.genderScore())
                        .trustScoreNorm(b.trustScoreNorm())
                        .freshness(b.freshnessScore())
                        .promotedBoost(b.promotedBoost())
                        .appliedWeightSum(b.appliedWeightSum())
                        .build())
                .matchReasons(b.matchReasons())
                .build();
    }

    /** Thẻ tin đơn giản cho cold-start (không có breakdown chi tiết). */
    public RecommendationResponse.RecommendationItem toColdItem(ListingAttr a, BigDecimal score,
                                                                java.util.List<String> reasons) {
        return RecommendationResponse.RecommendationItem.builder()
                .id(a.id())
                .slug(a.slug())
                .title(a.title())
                .categoryCode(a.categoryCode() == null ? null : a.categoryCode().name())
                .categoryName(a.categoryName())
                .price(a.price())
                .area(a.area())
                .shortAddress(a.shortAddress())
                .thumbnailUrl(a.thumbnailUrl())
                .trustScore(a.trustScore())
                .averageRating(a.averageRating())
                .promoted(a.promoted())
                .matchScore(score)
                .matchReasons(reasons)
                .build();
    }

    public RecommendationLog toLog(Long userId, String sessionId, RecommendationSource source,
                                   String batchId, ScoredListing scored, int rankPosition,
                                   boolean coldStart) {
        ScoreBreakdown b = scored.breakdown();
        return RecommendationLog.builder()
                .userId(userId)
                .sessionId(sessionId)
                .listingId(scored.listing().id())
                .source(source)
                .batchId(batchId)
                .score(b.finalScore())
                .rankPosition(rankPosition)
                .areaScore(b.areaScore())
                .priceScore(b.priceScore())
                .categoryScore(b.categoryScore())
                .amenityScore(b.amenityScore())
                .trustScoreNorm(b.trustScoreNorm())
                .freshnessScore(b.freshnessScore())
                .promotedBoost(b.promotedBoost())
                .isColdStart(coldStart)
                .context(contextJson(b))
                .build();
    }

    public RecommendationLog toColdLog(Long userId, String sessionId, RecommendationSource source,
                                       String batchId, Long listingId, BigDecimal score, int rankPosition) {
        return RecommendationLog.builder()
                .userId(userId)
                .sessionId(sessionId)
                .listingId(listingId)
                .source(source)
                .batchId(batchId)
                .score(score)
                .rankPosition(rankPosition)
                .promotedBoost(BigDecimal.ONE)
                .isColdStart(true)
                .context("{\"strategy\":\"COLD_START\"}")
                .build();
    }

    public RecommendationResponse.ProfileSummary toProfileSummary(UserPreferenceProfile p) {
        if (p == null || p.empty()) {
            return null;
        }
        return RecommendationResponse.ProfileSummary.builder()
                .preferredPriceLow(p.priceLow())
                .preferredPriceHigh(p.priceHigh())
                .preferredAreaLow(p.areaLow())
                .preferredAreaHigh(p.areaHigh())
                .preferredOccupants(p.preferredOccupants())
                .behaviorCounts(RecommendationResponse.BehaviorCounts.builder()
                        .views(p.counts().views())
                        .searches(p.counts().searches())
                        .favorites(p.counts().favorites())
                        .contacts(p.counts().contacts())
                        .build())
                .note("Hồ sơ dựng từ lịch sử xem (w=1), tìm kiếm (w=2), lưu tin (w=3), liên hệ (w=5)")
                .build();
    }

    private String contextJson(ScoreBreakdown b) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("locationScore", b.locationScore());
        ctx.put("occupantScore", b.occupantScore());
        ctx.put("genderScore", b.genderScore());
        ctx.put("appliedWeightSum", b.appliedWeightSum());
        try {
            return objectMapper.writeValueAsString(ctx);
        } catch (Exception e) {
            log.warn("Không serialize được context gợi ý: {}", e.getMessage());
            return "{}";
        }
    }
}
