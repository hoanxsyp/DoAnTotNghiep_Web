package com.webtro.modules.interaction.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Một mục lịch sử xem tin — {@code GET /api/history/views} (canonical 4.5.4).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ViewHistoryResponse", description = "Một mục lịch sử xem tin")
public class ViewHistoryResponse {

    private Long id;
    private Long listingId;
    private String title;
    private String slug;
    private String thumbnailUrl;
    private BigDecimal price;
    private BigDecimal area;
    private String shortAddress;
    private String status;

    @Schema(description = "Tin không còn hiển thị công khai")
    private Boolean notAvailable;

    @Schema(description = "Tin này có đang được người dùng lưu không")
    private Boolean favoritedByMe;

    @Schema(description = "Thời điểm xem gần nhất")
    private Instant viewedAt;
}
