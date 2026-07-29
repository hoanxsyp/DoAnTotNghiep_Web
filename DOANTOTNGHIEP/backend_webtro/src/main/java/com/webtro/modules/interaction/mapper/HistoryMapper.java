package com.webtro.modules.interaction.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.webtro.modules.interaction.dto.response.SearchHistoryResponse;
import com.webtro.modules.interaction.dto.response.ViewHistoryResponse;
import com.webtro.modules.interaction.entity.SearchHistory;
import com.webtro.modules.interaction.entity.ViewHistory;
import com.webtro.modules.interaction.spi.ListingGateway.ListingBrief;
import org.springframework.stereotype.Component;

/**
 * Ánh xạ ViewHistory/SearchHistory → DTO (thủ công, Builder).
 */
@Component
public class HistoryMapper {

    public ViewHistoryResponse toViewResponse(ViewHistory view, ListingBrief brief, boolean favoritedByMe) {
        ViewHistoryResponse.ViewHistoryResponseBuilder b = ViewHistoryResponse.builder()
                .id(view.getId())
                .listingId(view.getListingId())
                .favoritedByMe(favoritedByMe)
                .viewedAt(view.getViewedAt());

        if (brief == null) {
            return b.notAvailable(true).build();
        }
        return b.title(brief.title())
                .slug(brief.slug())
                .thumbnailUrl(brief.thumbnailUrl())
                .price(brief.price())
                .area(brief.area())
                .shortAddress(brief.shortAddress())
                .status(brief.status() == null ? null : brief.status().name())
                .notAvailable(!brief.publiclyVisible())
                .build();
    }

    /**
     * @param filters       cây JSON đã parse từ cột {@code criteria} (có thể null)
     * @param filterSummary tóm tắt bộ lọc dạng chữ (service dựng)
     */
    public SearchHistoryResponse toSearchResponse(SearchHistory history, JsonNode filters, String filterSummary) {
        return SearchHistoryResponse.builder()
                .id(history.getId())
                .keyword(history.getKeyword())
                .filterSummary(filterSummary)
                .filters(filters)
                .resultCount(history.getResultCount())
                .createdAt(history.getCreatedAt())
                .build();
    }
}
