package com.webtro.modules.auth.dto.request;

import com.webtro.validator.ValidPhone;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu gửi OTP xác thực số điện thoại (AUTH-06) — {@code POST /api/auth/send-phone-otp}.
 * Số điện thoại phải trùng với số của tài khoản đang đăng nhập (docs/03 mục 4.1.10).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "SendPhoneOtpRequest", description = "Số điện thoại cần xác thực")
public class SendPhoneOtpRequest {

    @Schema(description = "Số điện thoại cần xác thực", example = "0901234567")
    @NotBlank(message = "Vui lòng nhập số điện thoại")
    @ValidPhone
    private String phone;
}
