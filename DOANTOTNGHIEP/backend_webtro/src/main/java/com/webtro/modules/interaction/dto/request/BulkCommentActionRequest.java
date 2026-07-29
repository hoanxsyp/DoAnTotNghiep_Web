package com.webtro.modules.interaction.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Body cho {@code PUT /api/admin/comments/bulk}. Áp một hành động kiểm duyệt cho nhiều bình luận:
 * mỗi bình luận được xử lý độc lập, kết quả trả về danh sách thành công/thất bại.
 */
@Getter
@Setter
@Schema(name = "BulkCommentActionRequest", description = "Thao tác kiểm duyệt bình luận hàng loạt")
public class BulkCommentActionRequest {

    @Schema(description = "Danh sách id bình luận cần xử lý", example = "[5, 6, 7]")
    @NotEmpty(message = "Danh sách bình luận không được rỗng")
    @Size(max = 100, message = "Mỗi lần chỉ xử lý tối đa 100 bình luận")
    private List<Long> ids;

    @Schema(description = "Hành động áp dụng", example = "HIDE", allowableValues = {"HIDE", "SPAM"})
    @NotNull(message = "Vui lòng chọn hành động")
    @Pattern(regexp = "HIDE|SPAM", message = "Hành động phải thuộc {HIDE, SPAM}")
    private String action;

    @Schema(description = "Lý do (bắt buộc với HIDE)", example = "Nội dung xúc phạm, vi phạm quy định cộng đồng")
    private String reason;
}
