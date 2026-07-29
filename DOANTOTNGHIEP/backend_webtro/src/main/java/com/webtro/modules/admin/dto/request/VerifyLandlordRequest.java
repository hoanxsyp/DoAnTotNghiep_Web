package com.webtro.modules.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Body cho {@code PUT /api/admin/landlords/{id}/verify} (canonical 4.13.7).
 * Ghi chú nội bộ là tùy chọn.
 */
@Getter
@Setter
@Schema(name = "VerifyLandlordRequest", description = "Yêu cầu xác thực chủ trọ")
public class VerifyLandlordRequest {

    @Schema(description = "Ghi chú nội bộ (tùy chọn)", example = "Đã đối chiếu giấy tờ hợp lệ")
    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String note;
}
