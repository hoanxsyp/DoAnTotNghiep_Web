package com.webtro.modules.interaction.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Một bình luận nhìn từ màn hình kiểm duyệt ({@code GET /api/admin/comments} và các thao tác
 * ẩn/khôi phục/đánh dấu spam) — quyền {@code COMMENT_MODERATE}.
 *
 * <p>Khác {@link CommentResponse} công khai: bổ sung thông tin kiểm duyệt ({@code hidden*},
 * {@code isSpam}, {@code isRiskComment}, {@code containsBannedKeyword}) và nhãn cảm xúc đầy đủ để
 * kiểm duyệt viên ra quyết định.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AdminCommentResponse", description = "Bình luận (góc nhìn kiểm duyệt)")
public class AdminCommentResponse {

    private Long id;
    private Long listingId;
    private Long parentCommentId;
    private String content;

    @Schema(description = "Trạng thái (VISIBLE/PENDING/HIDDEN/DELETED)")
    private String status;

    @Schema(description = "Nhãn trạng thái tiếng Việt")
    private String statusLabel;

    @Schema(description = "Tác giả bình luận")
    private AuthorResponse author;

    @Schema(description = "Nhãn cảm xúc do AI gán (gồm PENDING_ANALYSIS khi chưa phân tích)")
    private String sentimentLabel;

    @Schema(description = "Nhãn cảm xúc tiếng Việt")
    private String sentimentLabelText;

    @Schema(description = "Điểm cảm xúc [-1, 1]")
    private BigDecimal sentimentScore;

    @Schema(description = "Độ tin cậy phân tích [0, 1]")
    private BigDecimal sentimentConfidence;

    @Schema(description = "Bị đánh dấu spam (đã loại khỏi công thức tính uy tín)")
    private Boolean isSpam;

    @Schema(description = "Thuộc nhóm rủi ro cần theo dõi")
    private Boolean isRiskComment;

    @Schema(description = "Chứa từ khóa cấm mức nhẹ")
    private Boolean containsBannedKeyword;

    @Schema(description = "Là phản hồi của chính chủ tin")
    private Boolean isOwnerReply;

    @Schema(description = "Số lượt trả lời")
    private Integer replyCount;

    @Schema(description = "Lý do bị ẩn (chỉ có khi đã ẩn)")
    private String hiddenReason;

    @Schema(description = "Người ẩn (users.id)")
    private Long hiddenBy;

    @Schema(description = "Thời điểm bị ẩn")
    private Instant hiddenAt;

    private Instant editedAt;
    private Instant createdAt;
}
