package com.webtro.modules.interaction.dto.request;

import com.webtro.validator.NoHtml;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu sửa bình luận — {@code PUT /api/comments/{id}} (canonical 4.7.4).
 * Dùng chung cho {@code POST /api/comments/{id}/reply} (chỉ có {@code content}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "UpdateCommentRequest", description = "Yêu cầu sửa/trả lời bình luận")
public class UpdateCommentRequest {

    @Schema(description = "Nội dung mới (3–1000 ký tự)", example = "Phòng có cho nuôi mèo nhỏ không anh?")
    @NotBlank(message = "Nội dung bình luận không được để trống")
    @Size(min = 3, max = 1000, message = "Nội dung bình luận phải từ 3 đến 1000 ký tự")
    @NoHtml
    private String content;
}
