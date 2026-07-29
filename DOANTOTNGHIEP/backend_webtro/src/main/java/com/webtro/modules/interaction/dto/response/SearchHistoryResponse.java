package com.webtro.modules.interaction.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Một mục lịch sử tìm kiếm — {@code GET /api/search/histories} (canonical 4.5.7).
 * {@code filters} là JSON bộ lọc để FE tái áp dụng.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "SearchHistoryResponse", description = "Một mục lịch sử tìm kiếm")
public class SearchHistoryResponse {

    private Long id;
    private String keyword;

    @Schema(description = "Tóm tắt bộ lọc dạng chữ", example = "Quận Bình Thạnh · 2 – 4 triệu · Phòng trọ")
    private String filterSummary;

    @Schema(description = "Bộ lọc dạng JSON để tái áp dụng bằng một cú bấm")
    private JsonNode filters;

    private Integer resultCount;
    private Instant createdAt;
}
