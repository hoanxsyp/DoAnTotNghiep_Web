package com.webtro.modules.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu gửi liên kết đặt lại mật khẩu (AUTH-04) — {@code POST /api/auth/forgot-password}.
 * Luôn trả 200 kể cả email không tồn tại để chống dò tài khoản (docs/03 mục 4.1.5).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ForgotPasswordRequest", description = "Email cần đặt lại mật khẩu")
public class ForgotPasswordRequest {

    @Schema(description = "Email tài khoản", example = "nguyen.van.an@gmail.com")
    @NotBlank(message = "Vui lòng nhập email")
    @Email(message = "Email không đúng định dạng")
    @Size(max = 150, message = "Email không được vượt quá 150 ký tự")
    private String email;
}
