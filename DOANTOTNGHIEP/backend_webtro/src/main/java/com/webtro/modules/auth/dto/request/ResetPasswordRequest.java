package com.webtro.modules.auth.dto.request;

import com.webtro.validator.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu đặt lại mật khẩu bằng token trong email (AUTH-04) — {@code POST /api/auth/reset-password}.
 * Đổi mật khẩu thành công sẽ thu hồi toàn bộ refresh token của người dùng (docs/03 mục 4.1.6).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ResetPasswordRequest", description = "Token đặt lại và mật khẩu mới")
public class ResetPasswordRequest {

    @Schema(description = "Token 64 ký tự trong email đặt lại mật khẩu")
    @NotBlank(message = "Thiếu mã đặt lại mật khẩu")
    @Size(min = 32, max = 64, message = "Mã đặt lại mật khẩu không hợp lệ")
    private String token;

    @Schema(description = "Mật khẩu mới (tối thiểu 8 ký tự, có chữ và số)", example = "MatKhauMoi123")
    @NotBlank(message = "Vui lòng nhập mật khẩu mới")
    @ValidPassword
    private String newPassword;

    @Schema(description = "Nhập lại mật khẩu mới", example = "MatKhauMoi123")
    @NotBlank(message = "Vui lòng xác nhận mật khẩu mới")
    private String confirmPassword;
}
