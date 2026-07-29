package com.webtro.modules.interaction.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lý do khi kiểm duyệt viên ẩn bình luận/đánh giá vi phạm ({@code PUT /api/admin/comments/{id}/hide},
 * {@code PUT /api/admin/reviews/{id}/hide}). Bắt buộc để ghi {@code hidden_reason} và AuditLog.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ModerationReasonRequest", description = "Lý do kiểm duyệt")
public class ModerationReasonRequest {

    @Schema(description = "Lý do ẩn nội dung", example = "Nội dung xúc phạm, vi phạm quy định cộng đồng")
    @NotBlank(message = "Vui lòng nhập lý do kiểm duyệt")
    @Size(min = 10, max = 500, message = "Lý do kiểm duyệt phải từ 10 đến 500 ký tự")
    private String reason;
}
