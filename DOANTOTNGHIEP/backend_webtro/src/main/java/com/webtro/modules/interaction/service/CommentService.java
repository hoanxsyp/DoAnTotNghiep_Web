package com.webtro.modules.interaction.service;

import com.webtro.common.PageResponse;
import com.webtro.common.enums.CommentStatus;
import com.webtro.common.enums.SentimentLabel;
import com.webtro.modules.interaction.dto.request.CreateCommentRequest;
import com.webtro.modules.interaction.dto.request.UpdateCommentRequest;
import com.webtro.modules.interaction.dto.response.AdminCommentResponse;
import com.webtro.modules.interaction.dto.response.CommentPageResponse;
import com.webtro.modules.interaction.dto.response.CommentResponse;
import org.springframework.data.domain.Pageable;

/**
 * Nghiệp vụ bình luận — CMT, canonical mục 4.7, {@code [§3.11]}.
 */
public interface CommentService {

    /** Danh sách bình luận của tin (ẩn danh); lồng một cấp; kèm thống kê cảm xúc. */
    CommentPageResponse listComments(Long listingId, boolean includeReplies, Long viewerId, Pageable pageable);

    /** Tạo bình luận (rate limit, quét từ khóa cấm, publish {@code CommentCreatedEvent} cho AI). */
    CommentResponse createComment(Long listingId, CreateCommentRequest request, Long userId);

    /** Trả lời một bình luận gốc (đường tắt của tạo bình luận với {@code parentCommentId} từ path). */
    CommentResponse replyComment(Long parentCommentId, UpdateCommentRequest request, Long userId);

    /** Sửa bình luận trong cửa sổ {@code comment.edit_window_minutes}; kích hoạt phân tích lại. */
    CommentResponse updateComment(Long commentId, UpdateCommentRequest request, Long userId);

    /** Xóa mềm bình luận (tác giả trong cửa sổ, hoặc {@code COMMENT_MODERATE}). */
    void deleteComment(Long commentId, Long userId);

    /**
     * Ẩn bình luận do kiểm duyệt ({@code status = HIDDEN}, ghi {@code hidden_reason/by/at}); đồng bộ
     * lại {@code comment_count}. Gọi bởi adapter {@code ContentModerationGateway} của module moderation.
     */
    void hideByModeration(Long commentId, Long moderatorId, String reason);

    // ------------------ Kiểm duyệt (COMMENT_MODERATE) — canonical 4.7.5 ------------------

    /**
     * Danh sách bình luận cho màn hình kiểm duyệt: lọc theo trạng thái / nhãn cảm xúc / cờ spam /
     * từ khóa nội dung / tin, phân trang; kèm nhãn cảm xúc và thông tin ẩn.
     */
    PageResponse<AdminCommentResponse> adminListComments(CommentStatus status, SentimentLabel sentiment,
                                                         Boolean isSpam, String keyword, Long listingId,
                                                         Pageable pageable);

    /**
     * Ẩn bình luận vi phạm (dùng cho endpoint admin): đặt {@code HIDDEN}, ghi lý do/người/thời điểm,
     * đồng bộ số đếm và ghi AuditLog {@code COMMENT_HIDE}. Trả bản ghi sau khi ẩn.
     */
    AdminCommentResponse adminHideComment(Long commentId, Long moderatorId, String reason);

    /**
     * Khôi phục bình luận đã ẩn về {@code VISIBLE} (xóa {@code hidden_reason/by/at}); đồng bộ số đếm
     * và ghi AuditLog {@code COMMENT_UNHIDE}. Trả bản ghi sau khi khôi phục.
     */
    AdminCommentResponse unhideByModeration(Long commentId, Long moderatorId);

    /**
     * Đánh dấu bình luận là spam ({@code is_spam = true}, trọng số uy tín = 0 để loại khỏi công thức
     * tính điểm uy tín) và ghi AuditLog {@code COMMENT_SPAM}. Trả bản ghi sau khi cập nhật.
     */
    AdminCommentResponse markSpam(Long commentId, Long moderatorId);
}
