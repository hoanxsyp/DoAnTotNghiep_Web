package com.webtro.modules.ai.entity;

import com.webtro.common.enums.SentimentAction;
import com.webtro.common.enums.SentimentLabel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Kết quả phân tích cảm xúc của một bình luận ({@code sentiment_results}) — mục 39 của V1 baseline.
 *
 * <p><b>Append-only</b> (chỉ {@code id} + {@code created_at}): một bình luận có thể có nhiều phiên
 * bản phân tích, cờ {@code is_latest} đánh dấu bản hiện hành. Vì bảng không có
 * {@code updated_at}/{@code created_by}/{@code updated_by}/{@code deleted_at} nên <b>không kế thừa</b>
 * {@link com.webtro.common.AuditableEntity}/{@link com.webtro.common.BaseEntity} (kế thừa sẽ ánh xạ
 * cột không tồn tại và fail {@code ddl-auto=validate}); {@code id}/{@code created_at} khai tại chỗ.
 *
 * <p>{@code commentId} trỏ sang module khác (chéo module) nên giữ {@code Long}, không map quan hệ
 * (canonical luật 8). Listing suy ra qua comment.
 *
 * <p>Cột sinh {@code latest_uk} (STORED, = {@code IF(is_latest, comment_id, NULL)}) do DB tự sinh
 * để ép "đúng một" phiên bản hiện hành/bình luận — <b>không map</b> ở entity.
 */
@Entity
@Table(
        name = "sentiment_results",
        indexes = {
                @Index(name = "idx_sentiment_results_comment_id_created_at", columnList = "comment_id, created_at"),
                @Index(name = "idx_sentiment_results_suggested_action", columnList = "suggested_action, is_latest"),
                @Index(name = "idx_sentiment_results_analyzer_version", columnList = "analyzer_version, created_at"),
                @Index(name = "idx_sentiment_results_label_confidence", columnList = "label, confidence")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SentimentResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /** Bình luận được phân tích (FK comments.id — chéo module, giữ Long). */
    @Column(name = "comment_id", nullable = false)
    private Long commentId;

    /** Tin đăng chứa bình luận (denormalize để tính tỷ lệ tiêu cực; FK listings.id — chéo module). */

    /** Nhãn cảm xúc. */
    @Enumerated(EnumType.STRING)
    @Column(name = "label", nullable = false, length = 20)
    private SentimentLabel label;

    /** Điểm cảm xúc [-1, 1]. */
    @Column(name = "score", precision = 4, scale = 3)
    private BigDecimal score;

    /** Độ tin cậy [0, 1]. */
    @Column(name = "confidence", precision = 4, scale = 3)
    private BigDecimal confidence;

    /** Bình luận có dấu hiệu rủi ro (lừa đảo/tố cáo...). */
    @Builder.Default
    @Column(name = "is_risk_comment", nullable = false)
    private Boolean isRiskComment = false;

    /** Hành động đề xuất cho kiểm duyệt. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "suggested_action", nullable = false, length = 15)
    private SentimentAction suggestedAction = SentimentAction.NONE;

    /** Trọng số của bình luận khi gộp điểm uy tín [0, 1]. */
    @Builder.Default
    @Column(name = "weight", nullable = false, precision = 3, scale = 2)
    private BigDecimal weight = BigDecimal.ONE;

    /** Danh sách từ tích cực khớp (JSON thô). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "matched_positive_terms", columnDefinition = "json")
    private String matchedPositiveTerms;

    /** Danh sách từ tiêu cực khớp (JSON thô). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "matched_negative_terms", columnDefinition = "json")
    private String matchedNegativeTerms;

    /** Có áp dụng xử lý phủ định ("không tốt") khi chấm điểm không. */
    @Builder.Default
    @Column(name = "negation_applied", nullable = false)
    private Boolean negationApplied = false;

    /** Phiên bản bộ phân tích (đối chiếu với ai_configs.version). */
    @Column(name = "analyzer_version", nullable = false, length = 20)
    private String analyzerVersion;

    /** Thời gian xử lý (ms). */
    @Column(name = "processing_ms")
    private Integer processingMs;

    /** Thông báo lỗi khi phân tích thất bại. */
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /** Số lần thử lại. */
    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    /** Phiên bản hiện hành của bình luận (ép đúng 1 qua cột sinh latest_uk). */
    @Builder.Default
    @Column(name = "is_latest", nullable = false)
    private Boolean isLatest = true;

    /** Thời điểm phân tích xong ({@code null} khi còn PENDING_ANALYSIS). */
    @Column(name = "analyzed_at")
    private Instant analyzedAt;

    /** Thời điểm ghi nhận (append-only). */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
