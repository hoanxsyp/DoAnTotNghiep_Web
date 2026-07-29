package com.webtro.modules.interaction.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webtro.modules.ai.spi.InteractionSignalGateway;
import com.webtro.modules.interaction.entity.ContactLog;
import com.webtro.modules.interaction.entity.Favorite;
import com.webtro.modules.interaction.entity.SearchHistory;
import com.webtro.modules.interaction.entity.ViewHistory;
import com.webtro.modules.interaction.repository.ContactLogRepository;
import com.webtro.modules.interaction.repository.FavoriteRepository;
import com.webtro.modules.interaction.repository.SearchHistoryRepository;
import com.webtro.modules.interaction.repository.ViewHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter (canonical luật 4/7, mục 10.2 §9.2) — hiện thực SPI {@link InteractionSignalGateway} của
 * module {@code ai} bằng bốn repository hành vi của module {@code interaction}
 * ({@link ViewHistoryRepository}, {@link SearchHistoryRepository}, {@link FavoriteRepository},
 * {@link ContactLogRepository}).
 *
 * <p>Chỉ trả tín hiệu hành vi thô (id tin + trọng số nguồn + tiêu chí tìm kiếm); toàn bộ logic dựng
 * {@code UserPreferenceProfile} nằm ở module {@code ai}.
 *
 * <p><b>Trọng số nguồn</b> theo Javadoc SPI: xem = 1, lưu = 3, liên hệ = 5. Tín hiệu tìm kiếm
 * (trọng số 2) KHÔNG gộp vào {@link #recentBehaviorRefs} vì {@code SearchHistory} không gắn với một
 * tin cụ thể (không có {@code listingId}); nó được trả riêng qua {@link #recentSearchSignals}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InteractionSignalGatewayAdapter implements InteractionSignalGateway {

    private static final int WEIGHT_VIEW = 1;
    private static final int WEIGHT_FAVORITE = 3;
    private static final int WEIGHT_CONTACT = 5;

    private final ViewHistoryRepository viewHistoryRepository;
    private final SearchHistoryRepository searchHistoryRepository;
    private final FavoriteRepository favoriteRepository;
    private final ContactLogRepository contactLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * Gộp các tham chiếu hành vi gần đây từ ViewHistory (w=1), Favorite (w=3), ContactLog (w=5); mỗi
     * nguồn tối đa {@code maxPerSource} bản ghi mới nhất kể từ {@code since}.
     */
    @Override
    @Transactional(readOnly = true)
    public List<BehaviorRef> recentBehaviorRefs(Long userId, Instant since, int maxPerSource) {
        if (userId == null) {
            return List.of();
        }
        PageRequest cap = PageRequest.of(0, Math.max(1, maxPerSource));
        List<BehaviorRef> refs = new ArrayList<>();

        for (ViewHistory v : viewHistoryRepository
                .findByUserIdAndViewedAtAfterOrderByViewedAtDesc(userId, since, cap)) {
            refs.add(new BehaviorRef(v.getListingId(), WEIGHT_VIEW, v.getViewedAt()));
        }
        for (Favorite f : favoriteRepository
                .findByUserIdAndCreatedAtAfterAndDeletedAtIsNullOrderByCreatedAtDesc(userId, since, cap)) {
            refs.add(new BehaviorRef(f.getListingId(), WEIGHT_FAVORITE, f.getCreatedAt()));
        }
        for (ContactLog c : contactLogRepository
                .findByUserIdAndCreatedAtAfterAndDeletedAtIsNullOrderByCreatedAtDesc(userId, since, cap)) {
            refs.add(new BehaviorRef(c.getListingId(), WEIGHT_CONTACT, c.getCreatedAt()));
        }
        return refs;
    }

    /** Tiêu chí các lần tìm kiếm gần đây (w=2), tối đa {@code limit} bản ghi mới nhất. */
    @Override
    @Transactional(readOnly = true)
    public List<SearchSignal> recentSearchSignals(Long userId, int limit) {
        if (userId == null || limit <= 0) {
            return List.of();
        }
        return searchHistoryRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit))
                .getContent().stream()
                .map(this::toSearchSignal)
                .toList();
    }

    /** Id các tin (đã khử trùng) người dùng đã xem kể từ {@code since} — chống gợi ý lặp §9.2. */
    @Override
    @Transactional(readOnly = true)
    public List<Long> recentlyViewedListingIds(Long userId, Instant since) {
        if (userId == null) {
            return List.of();
        }
        return viewHistoryRepository.findDistinctListingIdsByUserIdSince(userId, since);
    }

    /** Có bất kỳ tín hiệu hành vi nào không (xem/lưu/liên hệ/tìm kiếm) — quyết định cold-start. */
    @Override
    @Transactional(readOnly = true)
    public boolean hasBehaviorData(Long userId) {
        if (userId == null) {
            return false;
        }
        return viewHistoryRepository.existsByUserId(userId)
                || favoriteRepository.existsByUserIdAndDeletedAtIsNull(userId)
                || contactLogRepository.existsByUserIdAndDeletedAtIsNull(userId)
                || searchHistoryRepository.existsByUserId(userId);
    }

    // ------------------------------------------------------------------

    /** Đọc cột {@code criteria} (JSON của {@code ListingSearchRequest}) thành {@link SearchSignal}. */
    private SearchSignal toSearchSignal(SearchHistory history) {
        String criteria = history.getCriteria();
        if (criteria == null || criteria.isBlank()) {
            return new SearchSignal(null, null, null, null, null, null, null, null);
        }
        try {
            JsonNode n = objectMapper.readTree(criteria);
            return new SearchSignal(
                    asLong(n, "provinceId"),
                    asLong(n, "districtId"),
                    asLong(n, "wardId"),
                    asLong(n, "categoryId"),
                    asDecimal(n, "priceFrom"),
                    asDecimal(n, "priceTo"),
                    asDecimal(n, "areaFrom"),
                    asDecimal(n, "areaTo"));
        } catch (Exception e) {
            log.warn("Không parse được criteria của search_history id={}: {}", history.getId(), e.getMessage());
            return new SearchSignal(null, null, null, null, null, null, null, null);
        }
    }

    private static Long asLong(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asLong();
    }

    private static BigDecimal asDecimal(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.decimalValue();
    }
}
