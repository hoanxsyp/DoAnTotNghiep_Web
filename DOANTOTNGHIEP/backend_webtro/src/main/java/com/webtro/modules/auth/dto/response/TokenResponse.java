package com.webtro.modules.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Kết quả làm mới token ({@code POST /api/auth/refresh}). {@code refreshExpiresIn} là số giây còn
 * lại của HỌ token (không reset về 7 ngày) — docs/03 mục 4.1.3.
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

    @Schema(description = "Refresh token mới (cũng được đặt trong cookie httpOnly)")
    private String refreshToken;

    @Schema(description = "Loại token", example = "Bearer")
    private String tokenType;

    @Schema(description = "Số giây hiệu lực của access token", example = "900")
    private long expiresIn;

    @Schema(description = "Số giây còn lại của họ refresh token", example = "518400")
    private long refreshExpiresIn;
}
