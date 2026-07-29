package com.webtro.modules.search.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Kết quả endpoint "Tin liên quan" (docs/03 mục 4.4.5): nguồn gợi ý + danh sách tin tương tự.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "RelatedListingsResponse", description = "Danh sách tin liên quan")
public class RelatedListingsResponse {

    @Schema(description = "Nguồn gợi ý (RecommendationSource)", example = "SIMILAR_LISTING")
    private String source;

    @Schema(description = "Danh sách tin liên quan, đã sắp theo điểm khớp giảm dần")
    private List<RelatedListingItem> items;
}
