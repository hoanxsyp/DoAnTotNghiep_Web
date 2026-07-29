package com.webtro.modules.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu mở khóa tài khoản (canonical 4.13.4).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "UnlockUserRequest", description = "Yêu cầu mở khóa tài khoản")
public class UnlockUserRequest {

    @Schema(description = "Lý do mở khóa (10–500 ký tự, để ghi audit)")
    @NotBlank(message = "Vui lòng nhập lý do mở khóa")
    @Size(min = 10, max = 500, message = "Lý do phải từ 10 đến 500 ký tự")
    private String reason;

    @Schema(description = "Mở khóa luôn các tin đã bị khóa cùng lúc (LOCKED → HIDDEN)", example = "false")
    @Builder.Default
    private Boolean unlockListings = false;
}
