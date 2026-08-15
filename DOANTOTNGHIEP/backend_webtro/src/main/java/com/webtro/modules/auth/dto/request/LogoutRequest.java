package com.webtro.modules.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu đăng xuất (AUTH-03) — {@code POST /api/auth/logout}.
 *
 * <p>Client gửi refresh token đang giữ trong {@code localStorage}. Trường này KHÔNG bắt buộc vì
 * logout là idempotent: thiếu token, hoặc token không tồn tại, vẫn trả 204 (docs/03 mục 4.1.4) —
 * access token trong header vẫn bị đưa vào blacklist.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "LogoutRequest", description = "Refresh token của thiết bị hiện tại")
public class LogoutRequest {

    @Schema(description = "Refresh token của thiết bị hiện tại", nullable = true)
    private String refreshToken;
}
