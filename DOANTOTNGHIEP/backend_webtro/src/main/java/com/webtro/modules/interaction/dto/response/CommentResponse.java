package com.webtro.modules.interaction.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/**
 * Một bình luận — {@code GET/POST /api/listings/{id}/comments} và các endpoint sửa/trả lời
 * (canonical 4.7.1–4.7.4). Lồng một cấp qua {@code replies}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CommentResponse", description = "Một bình luận")
public class CommentResponse {

    private Long id;
    private Long listingId;
    private Long parentCommentId;
    private String content;
    private String status;
    private AuthorResponse author;

    @Schema(description = "Người xem hiện tại có sửa được không (tác giả + trong cửa sổ)")
    private Boolean editable;

    @Schema(description = "Thời điểm hết hạn sửa (chỉ có khi editable)")
    private Instant editableUntil;

    @Schema(description = "Người xem hiện tại có xóa được không")
    private Boolean deletable;

    private Instant editedAt;
    private Instant createdAt;

    @Schema(description = "Các trả lời một cấp")
    private List<CommentResponse> replies;
}
