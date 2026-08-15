package com.webtro.modules.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Kết quả đăng nhập (AUTH-02). Access token và refresh token đều trả trong body để client lưu trong
 * localStorage; backend không đặt cookie.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "LoginResponse", description = "Access + refresh token và thông tin người dùng")
public class LoginResponse {

    @Schema(description = "JWT access token (15 phút)")
    private String accessToken;

    @Schema(description = "Refresh token opaque để client lưu trong localStorage")
    private String refreshToken;

    @Schema(description = "Loại token", example = "Bearer")
    private String tokenType;

    @Schema(description = "Số giây hiệu lực của access token", example = "900")
    private long expiresIn;

    @Schema(description = "Số giây hiệu lực còn lại của refresh token", example = "86400")
    private long refreshExpiresIn;

    @Schema(description = "Thông tin người dùng")
    private AuthUserResponse user;
}
