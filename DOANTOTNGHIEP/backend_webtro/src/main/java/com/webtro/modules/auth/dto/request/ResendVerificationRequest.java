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
 * Yêu cầu gửi lại email xác thực — {@code POST /api/auth/resend-verification}.
 * Luôn trả 200 nếu email không tồn tại để chống dò tài khoản (docs/03 mục 4.1.9).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ResendVerificationRequest", description = "Email cần gửi lại xác thực")
public class ResendVerificationRequest {

    @Schema(description = "Email chưa xác thực", example = "nguyen.van.an@gmail.com")
    @NotBlank(message = "Vui lòng nhập email")
    @Email(message = "Email không đúng định dạng")
    @Size(max = 150, message = "Email không được vượt quá 150 ký tự")
    private String email;
}
