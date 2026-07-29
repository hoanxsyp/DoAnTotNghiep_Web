package com.webtro.modules.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Body cho {@code PUT /api/admin/landlords/{id}/reject-verification}. Từ chối yêu cầu xác thực chủ
 * trọ đang chờ duyệt; bắt buộc lý do (ghi vào {@code verification_note} + audit + thông báo).
 */
@Getter
@Setter
@Schema(name = "RejectLandlordVerificationRequest", description = "Yêu cầu từ chối xác thực chủ trọ")
public class RejectLandlordVerificationRequest {

    @Schema(description = "Lý do từ chối", example = "Ảnh giấy tờ mờ, không đối chiếu được thông tin.")
    @NotBlank(message = "Vui lòng nhập lý do từ chối xác thực")
    @Size(min = 10, max = 500, message = "Lý do phải từ 10 đến 500 ký tự")
    private String reason;
}
