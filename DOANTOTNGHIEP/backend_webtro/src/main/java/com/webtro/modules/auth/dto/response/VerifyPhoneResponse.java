package com.webtro.modules.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Kết quả xác thực số điện thoại (AUTH-06) — docs/03 mục 4.1.11.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "VerifyPhoneResponse", description = "Kết quả xác thực số điện thoại")
public class VerifyPhoneResponse {

    @Schema(description = "Số điện thoại đã xác thực", example = "0901234456")
    private String phone;

    @Schema(description = "Đã xác thực hay chưa", example = "true")
    private boolean phoneVerified;

    @Schema(description = "Thời điểm xác thực")
    private Instant verifiedAt;
}
