package com.webtro.modules.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu xác thực số điện thoại bằng OTP (AUTH-06) — {@code POST /api/auth/verify-phone}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "VerifyPhoneRequest", description = "Mã OTP 6 chữ số")
public class VerifyPhoneRequest {

    @Schema(description = "Mã OTP 6 chữ số", example = "123456")
    @NotBlank(message = "Vui lòng nhập mã OTP")
    @Pattern(regexp = "^[0-9]{6}$", message = "Mã OTP phải gồm 6 chữ số")
    private String otp;
}
