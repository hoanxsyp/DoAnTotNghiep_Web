package com.webtro.modules.ai.service.impl;

import com.webtro.common.enums.RecommendationSource;
import com.webtro.constant.ConfigKey;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessException;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.ai.dto.request.RecommendationRequest;
import com.webtro.modules.ai.dto.response.RecommendationResponse;
import com.webtro.modules.ai.engine.AiModuleSupport;
import com.webtro.modules.ai.engine.RecommendationEngine;
import com.webtro.modules.ai.engine.RecommendationEngine.ScoredListing;
import com.webtro.modules.ai.engine.RecommendationEngine.UserPreferenceProfile;
import com.webtro.modules.ai.engine.RecommendationEngine.WeightedListing;
import com.webtro.modules.ai.mapper.RecommendationMapper;
import com.webtro.modules.ai.repository.RecommendationLogRepository;
import com.webtro.modules.ai.spi.InteractionSignalGateway;
import com.webtro.modules.ai.spi.InteractionSignalGateway.BehaviorRef;
import com.webtro.modules.ai.spi.InteractionSignalGateway.SearchSignal;
import com.webtro.modules.ai.spi.ListingDataGateway;
import com.webtro.modules.ai.spi.ListingDataGateway.CandidateQuery;
import com.webtro.modules.ai.spi.ListingDataGateway.ListingAttr;
import com.webtro.modules.ai.spi.UserDataGateway;
import com.webtro.modules.ai.entity.RecommendationLog;
import com.webtro.modules.ai.service.RecommendationService;
import com.webtro.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hiện thực nghiệp vụ gợi ý tin đăng (canonical mục 10.2, §9.2).
 *
 * <p>Cửa sổ dựng hồ sơ và loại tin "đã xem gần đây" dùng {@code ai.recommendation.notify_active_user_days}
 * (30) — đọc từ cấu hình, không hardcode ngưỡng nghiệp vụ.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationServiceImpl implements RecommendationService {

    /** Giới hạn kỹ thuật khi nạp hành vi / ứng viên (không phải ngưỡng nghiệp vụ). */
    private static final int MAX_BEHAVIOR_PER_SOURCE = 100;
    private static final int SEARCH_SIGNAL_LIMIT = 20;
    private static final int CANDIDATE_FETCH_MULTIPLIER = 5;
    private static final int SEED_WEIGHT = 5;

    private final RecommendationEngine engine;
    private final RecommendationLogRepository recommendationLogRepository;
    private final InteractionSignalGateway signalGateway;
    private final ListingDataGateway listingGateway;
    private final UserDataGateway userGateway;
    private final SystemConfigService config;
    private final RecommendationMapper mapper;
    private final AiModuleSupport support;

    @Override
    @Transactional
    public RecommendationResponse recommend(RecommendationRequest request, CustomUserDetails principal,
                                            String sessionId) {
        support.ensureEnabled(ConfigKey.AI_RECOMMENDATION_ENABLED);

        RecommendationSource source = request.getSource();
        if ((source == RecommendationSource.SIMILAR_LISTING || source == RecommendationSource.AFTER_FAVORITE)
                && request.getListingId() == null) {
            throw new BusinessException(ErrorCode.MISSING_PARAMETER,
                    "Thiếu listingId cho ngữ cảnh " + source);
        }

        int size = clamp(request.getSize() == null ? config.getInt(ConfigKey.AI_RECOMMENDATION_SIZE)
                : request.getSize(), 1, 24);
        BigDecimal boostCap = config.getDecimal(ConfigKey.AI_RECOMMENDATION_PROMOTED_BOOST);
        int lookbackDays = config.getInt(ConfigKey.AI_RECOMMENDATION_NOTIFY_ACTIVE_USER_DAYS);
        Instant since = Instant.now().minus(lookbackDays, ChronoUnit.DAYS);

        Long userId = principal == null ? null : principal.getId();
        Set<Long> exclude = new HashSet<>();
        if (request.getExcludeListingIds() != null) {
            exclude.addAll(request.getExcludeListingIds());
        }
        if (request.getListingId() != null) {
            exclude.add(request.getListingId()); // không gợi ý lại chính tin gốc
        }

        // ----- Dựng hành vi + hồ sơ -----
        List<WeightedListing> behavior = new ArrayList<>();
        List<SearchSignal> searches = List.of();
        UserDataGateway.UserPreference pref = null;

        if (userId != null) {
            List<BehaviorRef> refs = signalGateway.recentBehaviorRefs(userId, since, MAX_BEHAVIOR_PER_SOURCE);
            List<Long> ids = refs.stream().map(BehaviorRef::listingId).distinct().toList();
            Map<Long, ListingAttr> attrs = listingGateway.getAttributes(ids);
            for (BehaviorRef r : refs) {
                ListingAttr a = attrs.get(r.listingId());
                if (a != null) {
                    behavior.add(new WeightedListing(a, r.weight()));
                }
            }
            searches = signalGateway.recentSearchSignals(userId, SEARCH_SIGNAL_LIMIT);
            pref = userGateway.preference(userId).orElse(null);
            // Loại tin đã xem gần đây (chống lặp §9.2).
            exclude.addAll(signalGateway.recentlyViewedListingIds(userId, since));
        }

        // Tin gốc (SIMILAR_LISTING/AFTER_FAVORITE) làm hạt giống hồ sơ.
        ListingAttr seed = null;
        if (request.getListingId() != null) {
            seed = listingGateway.getAttribute(request.getListingId()).orElse(null);
            if (seed != null) {
                behavior.add(new WeightedListing(seed, SEED_WEIGHT));
            }
        }

        final List<WeightedListing> behaviorFinal = behavior;
        final List<SearchSignal> searchesFinal = searches;
        final UserDataGateway.UserPreference prefFinal = pref;

        // Chấm điểm là phần CPU thuần → chạy có timeout (canonical mục 10).
        UserPreferenceProfile profile = support.runWithTimeout("recommendation",
                ConfigKey.AI_RECOMMENDATION_TIMEOUT_MS,
                () -> engine.buildProfile(behaviorFinal, searchesFinal, prefFinal));

        boolean personalized = !profile.empty();

        if (!personalized) {
            return coldStart(request, source, userId, sessionId, size, exclude);
        }

        // ----- Lấy ứng viên theo hồ sơ / tin gốc -----
        Long provinceHint = seed != null ? seed.provinceId() : argMax(profile.provinceWeights());
        Long districtHint = seed != null ? seed.districtId() : argMax(profile.districtWeights());
        Long categoryHint = seed != null ? seed.categoryId() : argMax(profile.categoryWeights());

        CandidateQuery query = new CandidateQuery(provinceHint, districtHint, null, categoryHint,
                profile.priceLow(), profile.priceHigh(), profile.areaLow(), profile.areaHigh(),
                exclude, userId, false, size * CANDIDATE_FETCH_MULTIPLIER);
        List<ListingAttr> candidates = new ArrayList<>(listingGateway.findCandidates(query));
        candidates.removeIf(c -> exclude.contains(c.id())
                || (userId != null && userId.equals(c.ownerId())));

        List<ScoredListing> scored = support.runWithTimeout("recommendation",
                ConfigKey.AI_RECOMMENDATION_TIMEOUT_MS,
                () -> engine.rank(profile, candidates, boostCap, size));

        // ----- Ghi RecommendationLog mọi lần (§9.2) -----
        String batchId = UUID.randomUUID().toString();
        List<RecommendationLog> logs = new ArrayList<>();
        int rank = 1;
        for (ScoredListing s : scored) {
            logs.add(mapper.toLog(userId, sessionId, source, batchId, s, rank++, false));
        }
        Long logId = persistLogs(logs);

        List<RecommendationResponse.RecommendationItem> items = new ArrayList<>();
        for (ScoredListing s : scored) {
            items.add(mapper.toItem(s));
        }

        return RecommendationResponse.builder()
                .source(source.name())
                .personalized(true)
                .coldStart(false)
                .recommendationLogId(logId)
                .batchId(batchId)
                .profileSummary(mapper.toProfileSummary(profile))
                .items(items)
                .generatedAt(Instant.now())
                .cacheHit(false)
                .build();
    }

    // ------------------------------------------------------------------
    //  Cold start
    // ------------------------------------------------------------------

    private RecommendationResponse coldStart(RecommendationRequest request, RecommendationSource source,
                                             Long userId, String sessionId, int size, Set<Long> exclude) {
        Long province = request.getProvinceId();
        Long district = request.getDistrictId();
        if (province == null && district == null) {
            // Không có gợi ý vị trí → tin mới nhất toàn hệ thống.
            CandidateQuery any = new CandidateQuery(null, null, null, null, null, null, null, null,
                    exclude, userId, true, size);
            return buildColdResponse(source, userId, sessionId, size, listingGateway.findCandidates(any));
        }
        CandidateQuery query = new CandidateQuery(province, district, null, null, null, null, null, null,
                exclude, userId, true, size);
        List<ListingAttr> candidates = listingGateway.findCandidates(query);
        return buildColdResponse(source, userId, sessionId, size, candidates);
    }

    private RecommendationResponse buildColdResponse(RecommendationSource source, Long userId,
                                                     String sessionId, int size, List<ListingAttr> candidates) {
        List<ListingAttr> picked = candidates.size() > size ? candidates.subList(0, size) : candidates;
        String batchId = UUID.randomUUID().toString();
        List<RecommendationLog> logs = new ArrayList<>();
        List<RecommendationResponse.RecommendationItem> items = new ArrayList<>();
        int rank = 1;
        for (ListingAttr a : picked) {
            BigDecimal score = BigDecimal.valueOf(Math.max(0.40, 0.60 - (rank - 1) * 0.01))
                    .setScale(4, java.math.RoundingMode.HALF_UP);
            List<String> reasons = List.of("Tin mới đăng", "Phổ biến trong khu vực");
            items.add(mapper.toColdItem(a, score, reasons));
            logs.add(mapper.toColdLog(userId, sessionId, source, batchId, a.id(), score, rank));
            rank++;
        }
        Long logId = persistLogs(logs);

        return RecommendationResponse.builder()
                .source(source.name())
                .personalized(false)
                .coldStart(true)
                .coldStartStrategy(List.of("NEWEST", "POPULAR_IN_AREA", "CURRENT_FILTER", "POPULAR_CATEGORY"))
                .coldStartNote("Chưa đủ dữ liệu hành vi — gợi ý tin mới nhất và phổ biến trong khu vực đang xem")
                .recommendationLogId(logId)
                .batchId(batchId)
                .profileSummary(null)
                .items(items)
                .generatedAt(Instant.now())
                .cacheHit(false)
                .build();
    }

    // ------------------------------------------------------------------

    private Long persistLogs(List<RecommendationLog> logs) {
        if (logs.isEmpty()) {
            return null;
        }
        List<RecommendationLog> saved = recommendationLogRepository.saveAll(logs);
        return saved.get(0).getId();
    }

    private Long argMax(Map<Long, BigDecimal> weights) {
        return weights.entrySet().stream()
                .max(Comparator.comparing(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
