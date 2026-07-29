package com.webtro.modules.auth.dto.request;

import com.webtro.validator.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu đổi mật khẩu khi đã đăng nhập (AUTH-05) — {@code POST /api/auth/change-password}.
 * Thu hồi các refresh token của thiết bị khác, giữ lại thiết bị hiện tại (docs/03 mục 4.1.7).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ChangePasswordRequest", description = "Mật khẩu cũ và mật khẩu mới")
public class ChangePasswordRequest {

    @Schema(description = "Mật khẩu hiện tại")
    @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại")
    private String oldPassword;

    @Schema(description = "Mật khẩu mới (tối thiểu 8 ký tự, có chữ và số, khác mật khẩu cũ)")
    @NotBlank(message = "Vui lòng nhập mật khẩu mới")
    @ValidPassword
    private String newPassword;

    @Schema(description = "Nhập lại mật khẩu mới")
    @NotBlank(message = "Vui lòng xác nhận mật khẩu mới")
    private String confirmPassword;
}
