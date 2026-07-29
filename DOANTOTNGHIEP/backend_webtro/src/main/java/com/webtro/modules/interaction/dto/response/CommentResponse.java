package com.webtro.modules.interaction.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
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

    @Schema(description = "Nhãn cảm xúc do AI gán (gồm PENDING_ANALYSIS khi chưa phân tích)")
    private String sentimentLabel;

    @Schema(description = "Điểm cảm xúc [-1, 1]")
    private BigDecimal sentimentScore;

    @Schema(description = "Người xem hiện tại có sửa được không (tác giả + trong cửa sổ)")
    private Boolean editable;

    @Schema(description = "Thời điểm hết hạn sửa (chỉ có khi editable)")
    private Instant editableUntil;

    @Schema(description = "Người xem hiện tại có xóa được không")
    private Boolean deletable;

    @Schema(description = "Đã kích hoạt phân tích lại sentiment (sau khi sửa)")
    private Boolean reanalysisTriggered;

    private Instant editedAt;
    private Instant createdAt;

    @Schema(description = "Các trả lời một cấp")
    private List<CommentResponse> replies;
}
