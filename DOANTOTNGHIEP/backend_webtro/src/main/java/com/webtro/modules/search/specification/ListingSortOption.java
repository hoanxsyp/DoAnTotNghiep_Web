package com.webtro.modules.search.specification;

import java.util.Arrays;
import java.util.Optional;

/**
 * Whitelist các giá trị sắp xếp hợp lệ của tìm kiếm (SRCH-08, docs/03 mục 4.4.1).
 *
 * <p><b>promotedPriority luôn là tiêu chí đầu</b> nhưng chỉ trong phạm vi kết quả đã lọc — được
 * áp trong {@link ListingSpecifications#orderBy}. Enum này chỉ mô tả tiêu chí THỨ HAI. Giá trị gửi
 * lên không nằm trong whitelist → service ném {@code INVALID_SORT_FIELD}.
 */
public enum ListingSortOption {

    /** Mặc định: mức ưu tiên tổng hợp (promoted + mới nhất). */
    RELEVANCE("RELEVANCE"),
    NEWEST("publishedAt,desc"),
    OLDEST("publishedAt,asc"),
    PRICE_ASC("price,asc"),
    PRICE_DESC("price,desc"),
    AREA_ASC("area,asc"),
    AREA_DESC("area,desc"),
    VIEW_DESC("viewCount,desc"),
    TRUST_DESC("trustScore,desc"),
    DISTANCE_ASC("distance,asc");

    private final String param;

    ListingSortOption(String param) {
        this.param = param;
    }

    public String getParam() {
        return param;
    }

    /**
     * Ánh xạ chuỗi query param sang enum. {@code null}/rỗng → {@link #RELEVANCE}.
     *
     * @return {@link Optional#empty()} nếu chuỗi không nằm trong whitelist (để service báo lỗi).
     */
    public static Optional<ListingSortOption> from(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.of(RELEVANCE);
        }
        String normalized = raw.trim();
        return Arrays.stream(values())
                .filter(o -> o.param.equalsIgnoreCase(normalized))
                .findFirst();
    }
}
