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
 * Yêu cầu mở khóa tin (canonical 4.14.6). Kết quả: LOCKED → HIDDEN (không phải ACTIVE).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "UnlockListingRequest", description = "Yêu cầu mở khóa tin đăng")
public class UnlockListingRequest {

    @Schema(description = "Lý do mở khóa (10–500 ký tự)")
    @NotBlank(message = "Vui lòng nhập lý do mở khóa")
    @Size(min = 10, max = 500, message = "Lý do phải từ 10 đến 500 ký tự")
    private String reason;
}
