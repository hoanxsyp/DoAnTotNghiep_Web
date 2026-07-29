package com.webtro.modules.search.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.webtro.modules.listing.dto.response.ListingSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Một tin liên quan: tái dùng {@link ListingSummaryResponse} của module listing (KHÔNG tạo lại DTO
 * tóm tắt) và bổ sung điểm/lý do khớp để FE hiển thị "Vì sao gợi ý" (SRCH-09, {@code [§9.2]}).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "RelatedListingItem", description = "Tin liên quan kèm điểm và lý do khớp")
public class RelatedListingItem {

    @Schema(description = "Thông tin tóm tắt tin (dùng chung mọi danh sách tin)")
    private ListingSummaryResponse listing;

    @Schema(description = "Điểm tương đồng 0..1", example = "0.87")
    private double matchScore;

    @Schema(description = "Các lý do khớp (tiếng Việt) để giải thích gợi ý")
    private List<String> matchReasons;
}
