package com.webtro.modules.catalog.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body bật/tắt hiển thị một tài nguyên catalog — mục 4.17.16–4.17.20. Dùng chung cho cả 5 tài
 * nguyên (danh mục, tiện ích, tỉnh/huyện/xã).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ToggleRequest", description = "Bật/tắt hiển thị")
public class ToggleRequest {

    @NotNull(message = "Trạng thái hiển thị không được để trống")
    @Schema(description = "Ghi vào cột is_active của tài nguyên", example = "false")
    private Boolean active;

    @Size(max = 255, message = "Ghi chú tối đa 255 ký tự")
    @Schema(description = "Ghi chú lý do (đưa vào audit_logs)")
    private String reason;
}
