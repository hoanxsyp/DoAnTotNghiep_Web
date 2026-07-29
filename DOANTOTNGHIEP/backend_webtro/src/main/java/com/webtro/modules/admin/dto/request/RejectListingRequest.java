package com.webtro.modules.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu từ chối tin (canonical 4.14.4). {@code reason} kiểm tra rỗng ở service để trả đúng mã
 * {@code REJECT_REASON_REQUIRED}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "RejectListingRequest", description = "Yêu cầu từ chối tin đăng")
public class RejectListingRequest {

    @Schema(description = "Lý do từ chối (10–500 ký tự, chủ trọ nhận được)")
    @Size(min = 10, max = 500, message = "Lý do phải từ 10 đến 500 ký tự")
    private String reason;

    @Schema(description = "Mã lý do (thống kê)", example = "FAKE_IMAGE")
    private String reasonCode;

    @Schema(description = "Ghi chú nội bộ (chủ trọ không thấy, ≤500 ký tự)")
    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String note;
}
