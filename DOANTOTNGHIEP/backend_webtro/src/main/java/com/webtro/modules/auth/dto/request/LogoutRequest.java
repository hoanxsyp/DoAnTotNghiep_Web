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
 * <p>Refresh token ưu tiên đọc từ cookie; body chỉ là dự phòng nên không bắt buộc. Logout là
 * idempotent: token không tồn tại vẫn trả 204 (docs/03 mục 4.1.4).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "LogoutRequest", description = "Refresh token của thiết bị hiện tại (dự phòng khi không dùng cookie)")
public class LogoutRequest {

    @Schema(description = "Refresh token của thiết bị hiện tại", nullable = true)
    private String refreshToken;
}
