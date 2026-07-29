package com.webtro.modules.interaction.entity;

import com.webtro.common.AuditableEntity;
import com.webtro.common.enums.CommentStatus;
import com.webtro.common.enums.SentimentLabel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Bình luận trên tin đăng — bảng {@code comments}. Hỗ trợ trả lời 1 cấp qua {@code parent}
 * (tự tham chiếu, trong cùng module) và lưu kết quả phân tích cảm xúc do module AI ghi về.
 *
 * <p>FK chéo module ({@code listingId}, {@code userId}, {@code hiddenBy}) giữ {@code Long}.
 * Các cột {@code sentiment_*} do {@code SentimentAnalyzer} (module ai) cập nhật qua service của
 * module interaction (không @Autowired ngược — canonical luật 7/8).
 */
@Entity
@Table(
        name = "comments",
        indexes = {
                @Index(name = "idx_comments_listing_id_status_created_at", columnList = "listing_id, status, created_at"),
                @Index(name = "idx_comments_parent_id", columnList = "parent_id"),
                @Index(name = "idx_comments_user_id_created_at", columnList = "user_id, created_at"),
                @Index(name = "idx_comments_sentiment_label_status", columnList = "sentiment_label, status"),
                @Index(name = "idx_comments_listing_id_sentiment", columnList = "listing_id, sentiment_label, is_spam"),
                @Index(name = "idx_comments_is_risk_comment", columnList = "is_risk_comment, status")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Comment extends AuditableEntity {

    /** Tin được bình luận (FK listings.id — chéo module, giữ Long). */
    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    /** Người bình luận (FK users.id — chéo module, giữ Long). */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Bình luận cha khi đây là câu trả lời (tự tham chiếu, trong module). {@code null} = bình luận gốc. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    @Column(name = "content", nullable = false, length = 1000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 10)
    private CommentStatus status = CommentStatus.VISIBLE;

    /** Nhãn cảm xúc do AI gán; mặc định chờ phân tích. */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "sentiment_label", nullable = false, length = 20)
    private SentimentLabel sentimentLabel = SentimentLabel.PENDING_ANALYSIS;

    /** Điểm cảm xúc [-1, 1]. */
    @Column(name = "sentiment_score", precision = 4, scale = 3)
    private BigDecimal sentimentScore;

    /** Độ tin cậy phân tích [0, 1]. */
    @Column(name = "sentiment_confidence", precision = 4, scale = 3)
    private BigDecimal sentimentConfidence;

    /** Trọng số bình luận trong công thức uy tín [0, 1] (tài khoản mới → thấp hơn). */
    @Builder.Default
    @Column(name = "sentiment_weight", nullable = false, precision = 3, scale = 2)
    private BigDecimal sentimentWeight = new BigDecimal("1.00");

    /** Bình luận thuộc nhóm rủi ro cần theo dõi. */
    @Builder.Default
    @Column(name = "is_risk_comment", nullable = false)
    private Boolean isRiskComment = false;

    @Builder.Default
    @Column(name = "is_spam", nullable = false)
    private Boolean isSpam = false;

    /** Là phản hồi của chính chủ tin. */
    @Builder.Default
    @Column(name = "is_owner_reply", nullable = false)
    private Boolean isOwnerReply = false;

    @Builder.Default
    @Column(name = "reply_count", nullable = false)
    private Integer replyCount = 0;

    @Builder.Default
    @Column(name = "contains_banned_keyword", nullable = false)
    private Boolean containsBannedKeyword = false;

    /** Lý do ẩn — bắt buộc khi {@code status = HIDDEN} (ck_comments_hidden_reason). */
    @Column(name = "hidden_reason", length = 500)
    private String hiddenReason;

    /** Người ẩn (FK users.id — chéo module, giữ Long). */
    @Column(name = "hidden_by")
    private Long hiddenBy;

    @Column(name = "hidden_at")
    private Instant hiddenAt;

    /** Mốc chỉnh sửa gần nhất (trong cửa sổ {@code comment.edit_window_minutes}). */
    @Column(name = "edited_at")
    private Instant editedAt;
}
