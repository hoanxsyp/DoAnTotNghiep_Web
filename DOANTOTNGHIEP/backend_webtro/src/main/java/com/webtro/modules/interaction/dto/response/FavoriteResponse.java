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
 * Một tin trong danh sách đã lưu — {@code GET /api/favorites} (canonical 4.5.3).
 *
 * <p>{@code notAvailable = true} khi tin nằm ngoài {@code publicStatuses()} (hết hạn, bị gỡ...):
 * vẫn giữ trong danh sách kèm nhãn tiếng Việt {@code [§3.9]}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "FavoriteResponse", description = "Một tin đã lưu")
public class FavoriteResponse {

    @Schema(description = "Id bản ghi favorite", example = "9901")
    private Long id;

    private Long listingId;
    private String title;
    private String slug;
    private String thumbnailUrl;
    private BigDecimal price;
    private BigDecimal area;
    private String shortAddress;
    private String status;
    private String note;

    @Schema(description = "Thời điểm lưu tin")
    private Instant favoritedAt;

    @Schema(description = "Tin không còn hiển thị công khai (hết hạn/bị gỡ)")
    private Boolean notAvailable;

    @Schema(description = "Nhãn tiếng Việt giải thích tin không còn hiển thị", example = "Tin đã hết hạn hiển thị")
    private String notAvailableLabel;
}
