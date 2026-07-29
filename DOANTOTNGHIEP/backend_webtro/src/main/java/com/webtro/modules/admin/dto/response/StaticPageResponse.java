package com.webtro.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Nội dung tĩnh công khai (trang Giới thiệu / Điều khoản) đọc từ {@code system_configs}
 * ({@code page.about} / {@code page.terms}, canonical mục 9).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "StaticPageResponse", description = "Nội dung trang tĩnh công khai")
public class StaticPageResponse {

    @Schema(description = "Khóa cấu hình nguồn", example = "page.about")
    private String key;

    @Schema(description = "Nội dung HTML của trang", example = "<h1>Giới thiệu Webtro</h1>...")
    private String content;
}
