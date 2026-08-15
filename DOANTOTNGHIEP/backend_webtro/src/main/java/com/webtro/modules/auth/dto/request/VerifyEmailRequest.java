package com.webtro.modules.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu xác thực email bằng token trong link hoặc OTP trong email (AUTH-06) —
 * {@code POST /api/auth/verify-email}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "VerifyEmailRequest", description = "Token link hoặc email + OTP xác thực email")
public class VerifyEmailRequest {

    @Schema(description = "Token 64 ký tự trong link xác thực email")
    @Size(min = 32, max = 64, message = "Mã xác thực không hợp lệ")
    private String token;

    @Schema(description = "Email nhận OTP, bắt buộc khi xác thực bằng OTP", example = "nguyen.van.an@gmail.com")
    @Email(message = "Email không đúng định dạng")
    @Size(max = 150, message = "Email không được vượt quá 150 ký tự")
    private String email;

    @Schema(description = "Mã OTP 6 chữ số trong email, bắt buộc khi không dùng token", example = "123456")
    @Pattern(regexp = "^[0-9]{6}$", message = "Mã OTP phải gồm 6 chữ số")
    private String otp;
}
