package com.webtro.modules.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Body cho {@code PUT /api/admin/listings/{id}/request-edit}. Yêu cầu chủ trọ chỉnh sửa tin: KHÔNG
 * đổi trạng thái tin (state machine không có transition tương ứng) mà chỉ ghi nhận hành động kiểm
 * duyệt {@code REQUEST_EDIT} + gửi thông báo kèm lý do bắt buộc.
 */
@Getter
@Setter
@Schema(name = "RequestListingEditRequest", description = "Yêu cầu chủ trọ chỉnh sửa tin")
public class RequestListingEditRequest {

    @Schema(description = "Nội dung cần chủ trọ chỉnh sửa", example = "Vui lòng bổ sung ảnh thực tế và giá điện/nước.")
    @NotBlank(message = "Vui lòng nhập nội dung yêu cầu chỉnh sửa")
    @Size(min = 10, max = 500, message = "Nội dung yêu cầu phải từ 10 đến 500 ký tự")
    private String reason;
}
