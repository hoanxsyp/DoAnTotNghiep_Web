package com.webtro.modules.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Body cho {@code PUT /api/admin/landlords/{id}/unverify} (canonical 4.13.8).
 * Bắt buộc lý do (ghi vào {@code verification_note}).
 */
@Getter
@Setter
@Schema(name = "UnverifyLandlordRequest", description = "Yêu cầu hủy xác thực chủ trọ")
public class UnverifyLandlordRequest {

    @Schema(description = "Lý do hủy xác thực", example = "Giấy tờ hết hiệu lực, cần bổ sung bản cập nhật.")
    @NotBlank(message = "Vui lòng nhập lý do hủy xác thực")
    @Size(min = 10, max = 500, message = "Lý do phải từ 10 đến 500 ký tự")
    private String reason;
}
