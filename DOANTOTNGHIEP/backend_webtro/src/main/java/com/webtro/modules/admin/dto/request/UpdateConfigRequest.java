package com.webtro.modules.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Yêu cầu cập nhật cấu hình hệ thống (canonical 4.20.2) — cũng dùng cho cập nhật cấu hình AI
 * (canonical 4.19.6). Mỗi phần tử là một cặp {@code key/value}; {@code reason} bắt buộc để ghi audit.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "UpdateConfigRequest", description = "Cập nhật một hoặc nhiều cấu hình")
public class UpdateConfigRequest {

    @Schema(description = "Danh sách cấu hình cần đổi")
    @NotEmpty(message = "Danh sách cấu hình không được rỗng")
    @Valid
    private List<ConfigEntry> configs;

    @Schema(description = "Lý do thay đổi (bắt buộc để ghi audit)", example = "Điều chỉnh thời hạn hiển thị tin theo chính sách mới")
    @NotBlank(message = "Vui lòng nhập lý do thay đổi")
    @Size(min = 10, max = 500, message = "Lý do phải từ 10 đến 500 ký tự")
    private String reason;

    /** Một cặp khóa/giá trị cần cập nhật. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "ConfigEntry", description = "Một cặp khóa/giá trị cấu hình")
    public static class ConfigEntry {

        @Schema(description = "Khóa cấu hình", example = "listing.display_days")
        @NotBlank(message = "Khóa cấu hình không được rỗng")
        private String key;

        @Schema(description = "Giá trị mới (số/luận lý/chuỗi tùy kiểu của khóa)", example = "45")
        @NotNull(message = "Giá trị cấu hình không được null")
        private Object value;
    }
}
