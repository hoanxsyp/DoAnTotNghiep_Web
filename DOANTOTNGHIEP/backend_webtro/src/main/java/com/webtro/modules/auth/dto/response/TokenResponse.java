package com.webtro.modules.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Kết quả làm mới token ({@code POST /api/auth/refresh}). Mỗi lần refresh thành công sẽ xoay token
 * và cấp refresh token mới có hạn theo TTL hiện tại.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "TokenResponse", description = "Access + refresh token mới sau khi xoay vòng")
public class TokenResponse {

    @Schema(description = "JWT access token mới")
    private String accessToken;

    @Schema(description = "Refresh token mới để client lưu trong localStorage")
    private String refreshToken;

    @Schema(description = "Loại token", example = "Bearer")
    private String tokenType;

    @Schema(description = "Số giây hiệu lực của access token", example = "900")
    private long expiresIn;

    @Schema(description = "Số giây hiệu lực còn lại của refresh token mới", example = "86400")
    private long refreshExpiresIn;
}
