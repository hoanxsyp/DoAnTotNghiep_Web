package com.webtro.modules.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webtro.common.ApiResponse;
import com.webtro.constant.ConfigKey;
import com.webtro.modules.admin.dto.response.StaticPageResponse;
import com.webtro.modules.admin.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Nội dung tĩnh công khai (trang Giới thiệu / Điều khoản, docs/04 mục 5.1.12). Đọc từ
 * {@code system_configs} qua {@link SystemConfigService} (khóa {@code page.about} / {@code page.terms},
 * canonical mục 9) và trả HTML để FE hiển thị. Công khai — không yêu cầu đăng nhập.
 *
 * <p>Hai khóa này có {@code value_type = JSON} (một chuỗi JSON bao HTML), nên giá trị thô còn dấu
 * nháy + ký tự thoát; giải mã về chuỗi HTML thuần trước khi trả cho FE.
 */
@Slf4j
@RestController
@RequestMapping("/api/content")
@RequiredArgsConstructor
@Tag(name = "Nội dung tĩnh", description = "Trang Giới thiệu / Điều khoản công khai")
public class PublicContentController {

    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper;

    @GetMapping("/about")
    @Operation(summary = "Trang Giới thiệu", description = "Nội dung HTML trang Giới thiệu (page.about).")
    public ResponseEntity<ApiResponse<StaticPageResponse>> getAbout() {
        return ResponseEntity.ok(ApiResponse.success(load(ConfigKey.PAGE_ABOUT),
                "Lấy nội dung trang Giới thiệu thành công"));
    }

    @GetMapping("/terms")
    @Operation(summary = "Trang Điều khoản", description = "Nội dung HTML trang Điều khoản (page.terms).")
    public ResponseEntity<ApiResponse<StaticPageResponse>> getTerms() {
        return ResponseEntity.ok(ApiResponse.success(load(ConfigKey.PAGE_TERMS),
                "Lấy nội dung trang Điều khoản thành công"));
    }

    private StaticPageResponse load(String key) {
        return StaticPageResponse.builder()
                .key(key)
                .content(decodeHtml(systemConfigService.getString(key)))
                .build();
    }

    /**
     * Giá trị JSON lưu dạng chuỗi bao HTML (còn dấu nháy + ký tự thoát) — giải mã về HTML thuần.
     * Nếu không phải JSON-string hợp lệ thì trả nguyên văn để không mất nội dung.
     */
    private String decodeHtml(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("\"")) {
            try {
                return objectMapper.readValue(trimmed, String.class);
            } catch (Exception ex) {
                log.debug("Không giải mã được nội dung tĩnh dạng JSON-string: {}", ex.getMessage());
            }
        }
        return raw;
    }
}
