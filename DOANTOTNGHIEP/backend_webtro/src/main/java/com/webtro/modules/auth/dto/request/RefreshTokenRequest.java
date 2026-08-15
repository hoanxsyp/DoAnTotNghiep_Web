package com.webtro.modules.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu làm mới token (AUTH — refresh) — {@code POST /api/auth/refresh}.
 *
 * <p>Client giữ refresh token trong {@code localStorage} và gửi kèm trong body — hệ thống không
 * còn dùng cookie cho token nữa, nên trường này là BẮT BUỘC.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "RefreshTokenRequest", description = "Refresh token của thiết bị hiện tại")
public class RefreshTokenRequest {

    @Schema(description = "Refresh token opaque (UUID) do client lưu ở localStorage")
    @NotBlank(message = "Thiếu refresh token")
    private String refreshToken;
}
