package com.webtro.modules.moderation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu tạm bật/tắt một từ khóa cấm (canonical 4.20.8). Khác {@code DELETE} (xóa mềm) — chỉ đổi
 * cờ {@code is_active}, bật lại được ngay.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ToggleBannedKeywordRequest", description = "Bật/tắt từ khóa cấm")
public class ToggleBannedKeywordRequest {

    @NotNull(message = "Vui lòng chọn trạng thái bật/tắt")
    @Schema(description = "true = có hiệu lực; false = tạm ngừng", example = "false")
    private Boolean active;

    @Size(max = 255, message = "Ghi chú tối đa 255 ký tự")
    @Schema(description = "Ghi chú (vào audit) — tùy chọn")
    private String reason;
}
