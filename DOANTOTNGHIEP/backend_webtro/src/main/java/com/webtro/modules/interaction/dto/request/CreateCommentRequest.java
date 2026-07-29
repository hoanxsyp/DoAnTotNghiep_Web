package com.webtro.modules.interaction.dto.request;

import com.webtro.validator.NoHtml;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu tạo bình luận — {@code POST /api/listings/{id}/comments} (canonical 4.7.2).
 * Nội dung 3–1000 ký tự {@code [§3.11]}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CreateCommentRequest", description = "Yêu cầu tạo bình luận")
public class CreateCommentRequest {

    @Schema(description = "Nội dung bình luận (3–1000 ký tự)", example = "Phòng có cho nuôi mèo không anh?")
    @NotBlank(message = "Nội dung bình luận không được để trống")
    @Size(min = 3, max = 1000, message = "Nội dung bình luận phải từ 3 đến 1000 ký tự")
    @NoHtml
    private String content;

    @Schema(description = "Id bình luận gốc khi trả lời (bỏ trống nếu là bình luận gốc)", example = "4401")
    @Positive(message = "Id bình luận gốc không hợp lệ")
    private Long parentCommentId;
}
