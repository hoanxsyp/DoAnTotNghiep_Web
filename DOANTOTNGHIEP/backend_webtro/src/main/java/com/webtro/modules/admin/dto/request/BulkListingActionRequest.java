package com.webtro.modules.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Body cho {@code PUT /api/admin/listings/bulk}. Áp một hành động kiểm duyệt cho nhiều tin: mỗi tin
 * được xử lý độc lập, kết quả trả về danh sách thành công/thất bại.
 */
@Getter
@Setter
@Schema(name = "BulkListingActionRequest", description = "Thao tác kiểm duyệt tin hàng loạt")
public class BulkListingActionRequest {

    @Schema(description = "Danh sách id tin cần xử lý", example = "[10, 11, 12]")
    @NotEmpty(message = "Danh sách tin không được rỗng")
    @Size(max = 100, message = "Mỗi lần chỉ xử lý tối đa 100 tin")
    private List<Long> ids;

    @Schema(description = "Hành động áp dụng", example = "APPROVE",
            allowableValues = {"APPROVE", "REJECT", "LOCK", "HIDE"})
    @NotNull(message = "Vui lòng chọn hành động")
    @Pattern(regexp = "APPROVE|REJECT|LOCK|HIDE",
            message = "Hành động phải thuộc {APPROVE, REJECT, LOCK, HIDE}")
    private String action;

    @Schema(description = "Lý do (bắt buộc với REJECT và LOCK)", example = "Vi phạm quy định nội dung.")
    private String reason;
}
