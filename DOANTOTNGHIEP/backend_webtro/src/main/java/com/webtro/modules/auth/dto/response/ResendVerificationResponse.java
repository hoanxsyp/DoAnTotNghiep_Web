package com.webtro.modules.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Kết quả gửi lại email xác thực — chỉ trả thời gian chờ trước lần gửi tiếp theo.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ResendVerificationResponse", description = "Thời gian chờ trước lần gửi lại tiếp theo")
public class ResendVerificationResponse {

    @Schema(description = "Số giây phải chờ trước khi gửi lại", example = "60")
    private long cooldownSeconds;
}
