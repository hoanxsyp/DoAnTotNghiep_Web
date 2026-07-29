package com.webtro.modules.interaction.dto.request;

import com.webtro.validator.NoHtml;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu lưu (yêu thích) một tin đăng — {@code POST /api/favorites} (canonical 4.5.1).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CreateFavoriteRequest", description = "Yêu cầu lưu tin vào danh sách yêu thích")
public class CreateFavoriteRequest {

    @Schema(description = "Id tin đăng cần lưu", example = "1024")
    @NotNull(message = "Vui lòng chọn tin đăng cần lưu")
    @Positive(message = "Id tin đăng không hợp lệ")
    private Long listingId;

    @Schema(description = "Ghi chú riêng cho tin đã lưu", example = "Gần công ty, ưu tiên xem")
    @Size(max = 255, message = "Ghi chú tối đa 255 ký tự")
    @NoHtml
    private String note;
}
