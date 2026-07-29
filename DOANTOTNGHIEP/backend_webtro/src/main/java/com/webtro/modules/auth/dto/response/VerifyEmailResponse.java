package com.webtro.modules.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Kết quả xác thực email (AUTH-06). Sau khi thành công tài khoản chuyển sang {@code ACTIVE}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "VerifyEmailResponse", description = "Kết quả xác thực email")
public class VerifyEmailResponse {

    @Schema(description = "Id người dùng", example = "103")
    private Long userId;

    @Schema(description = "Email", example = "nguyen.van.an@gmail.com")
    private String email;

    @Schema(description = "Trạng thái tài khoản sau xác thực", example = "ACTIVE")
    private String status;

    @Schema(description = "Thời điểm xác thực")
    private Instant verifiedAt;
}
