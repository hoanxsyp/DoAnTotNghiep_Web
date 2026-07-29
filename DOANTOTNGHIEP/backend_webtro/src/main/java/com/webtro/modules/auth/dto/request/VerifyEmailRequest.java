package com.webtro.modules.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu xác thực email bằng token trong link (AUTH-06) — {@code POST /api/auth/verify-email}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "VerifyEmailRequest", description = "Token xác thực email")
public class VerifyEmailRequest {

    @Schema(description = "Token 64 ký tự trong link xác thực email")
    @NotBlank(message = "Thiếu mã xác thực")
    @Size(min = 32, max = 64, message = "Mã xác thực không hợp lệ")
    private String token;
}
