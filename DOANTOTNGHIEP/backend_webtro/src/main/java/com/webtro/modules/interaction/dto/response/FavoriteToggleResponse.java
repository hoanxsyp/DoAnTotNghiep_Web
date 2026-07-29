package com.webtro.modules.interaction.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Kết quả lưu tin — {@code POST /api/favorites} (canonical 4.5.1). Trả lại số lượt lưu mới của tin.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "FavoriteToggleResponse", description = "Kết quả lưu tin")
public class FavoriteToggleResponse {

    private Long id;
    private Long listingId;

    @Schema(description = "Đang được lưu (true sau khi lưu thành công)")
    private Boolean favorited;

    @Schema(description = "Tổng số lượt lưu của tin sau thao tác", example = "35")
    private Integer favoriteCount;

    private Instant createdAt;
}
