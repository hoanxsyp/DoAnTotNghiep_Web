package com.webtro.modules.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Kết quả gửi OTP xác thực số điện thoại (AUTH-06) — docs/03 mục 4.1.10.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "PhoneOtpResponse", description = "Thông tin OTP vừa gửi")
public class PhoneOtpResponse {

    @Schema(description = "Số điện thoại đã che", example = "0901***456")
    private String maskedPhone;

    @Schema(description = "Số giây hiệu lực của OTP", example = "300")
    private long expiresInSeconds;

    @Schema(description = "Số giây phải chờ trước khi gửi lại", example = "60")
    private long cooldownSeconds;
}
