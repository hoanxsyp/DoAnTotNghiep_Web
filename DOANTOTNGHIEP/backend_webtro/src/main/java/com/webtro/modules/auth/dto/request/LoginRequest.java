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
 * Yêu cầu đăng nhập (AUTH-02) — {@code POST /api/auth/login}.
 * Cho phép đăng nhập bằng email hoặc số điện thoại (docs/03 mục 4.1.2).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "LoginRequest", description = "Dữ liệu đăng nhập")
public class LoginRequest {

    @Schema(description = "Email hoặc số điện thoại", example = "nguyen.van.an@gmail.com")
    @NotBlank(message = "Vui lòng nhập email hoặc số điện thoại")
    @Size(max = 150, message = "Định danh đăng nhập quá dài")
    private String emailOrPhone;

    @Schema(description = "Mật khẩu", example = "MatKhau123")
    @NotBlank(message = "Vui lòng nhập mật khẩu")
    private String password;

    @Schema(description = "Mã captcha — bắt buộc sau khi đăng nhập sai nhiều lần", nullable = true)
    private String captchaToken;

    @Schema(description = "Ghi nhớ thiết bị (chỉ dùng để lưu user-agent)", example = "false")
    private boolean rememberDevice;
}
