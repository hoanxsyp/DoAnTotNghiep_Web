package com.webtro.modules.ai.spi;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Cổng (SPI) mà module {@code ai} cần từ module {@code interaction} để dựng
 * {@code UserPreferenceProfile} cho recommendation (canonical mục 10.2, §9.2). Module
 * {@code interaction} phải cung cấp một {@code @Component} hiện thực (bọc {@code ViewHistoryRepository}
 * + {@code SearchHistoryRepository} + {@code FavoriteRepository} + {@code ContactLogRepository}).
 *
 * <p>Chỉ trả <b>tín hiệu hành vi thô</b> (id tin + trọng số nguồn, tiêu chí tìm kiếm). Toàn bộ logic
 * suy ra hồ sơ (percentile giá/diện tích, mode số người ở, trọng số khu vực...) nằm trong module
 * {@code ai} — đúng yêu cầu "xây UserPreferenceProfile" thuộc về module ai.
 */
public interface InteractionSignalGateway {

    /**
     * Tham chiếu hành vi tới một tin kèm trọng số nguồn.
     *
     * @param listingId tin liên quan
     * @param weight    trọng số nguồn: xem=1, tìm kiếm=2 (nếu suy ra được tin), lưu=3, liên hệ=5
     * @param at        thời điểm hành vi (mới hơn có thể ưu tiên khi cần)
     */
    record BehaviorRef(Long listingId, int weight, Instant at) {
    }

    /** Tiêu chí một lần tìm kiếm đã lưu (suy ra khu vực/giá/diện tích/loại ưa thích). */
    record SearchSignal(
            Long provinceId,
            Long districtId,
            Long wardId,
            Long categoryId,
            BigDecimal priceFrom,
            BigDecimal priceTo,
            BigDecimal areaFrom,
            BigDecimal areaTo) {
    }

    /**
     * Các tham chiếu hành vi gần đây của người dùng từ ViewHistory (w=1), Favorite (w=3),
     * ContactLog (w=5), giới hạn mỗi nguồn {@code maxPerSource} bản ghi mới nhất kể từ {@code since}.
     */
    List<BehaviorRef> recentBehaviorRefs(Long userId, Instant since, int maxPerSource);

    /** Tiêu chí các lần tìm kiếm gần đây (w=2), tối đa {@code limit} bản ghi mới nhất. */
    List<SearchSignal> recentSearchSignals(Long userId, int limit);

    /** Id các tin người dùng đã xem gần đây (chống gợi ý lặp §9.2), kể từ {@code since}. */
    List<Long> recentlyViewedListingIds(Long userId, Instant since);

    /** Có đủ dữ liệu hành vi để cá nhân hóa không (quyết định cold-start). */
    boolean hasBehaviorData(Long userId);
}
