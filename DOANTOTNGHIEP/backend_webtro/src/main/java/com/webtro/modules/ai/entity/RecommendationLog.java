package com.webtro.modules.ai.entity;

import com.webtro.common.enums.RecommendationSource;
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
 * Nhật ký gợi ý tin đăng ({@code recommendation_logs}) — mục 40 của V1 baseline.
 *
 * <p><b>Append-only</b> (chỉ {@code id} + {@code created_at}): mỗi lần hiển thị một tin trong danh
 * sách gợi ý ghi một dòng, phục vụ chống gợi ý lặp và đo CTR. Vì không có
 * {@code updated_at}/xóa mềm nên <b>không kế thừa</b> base class; khai {@code id}/{@code created_at}
 * tại chỗ.
 *
 * <p>{@code userId} (nullable — khách ẩn danh dùng {@code sessionId}) và {@code listingId} trỏ sang
 * module khác nên giữ {@code Long} (canonical luật 8).
 */
@Entity
@Table(
        name = "recommendation_logs",
        indexes = {
                @Index(name = "idx_recommendation_logs_user_id_created_at", columnList = "user_id, created_at"),
                @Index(name = "idx_recommendation_logs_batch_id", columnList = "batch_id"),
                @Index(name = "idx_recommendation_logs_listing_id", columnList = "listing_id"),
                @Index(name = "idx_recommendation_logs_source_created_at", columnList = "source, created_at"),
                @Index(name = "idx_recommendation_logs_clicked_at", columnList = "clicked_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /** Người nhận gợi ý — {@code null} nếu khách ẩn danh (FK users.id — chéo module, giữ Long). */
    @Column(name = "user_id")
    private Long userId;

    /** Định danh phiên khách ẩn danh (UUID). */
    @Column(name = "session_id", columnDefinition = "CHAR(36)")
    private String sessionId;

    /** Tin được gợi ý (FK listings.id — chéo module, giữ Long). */
    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    /** Nguồn hiển thị gợi ý (trang chủ, tin tương tự, chatbot...). */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private RecommendationSource source;

    /** Mã lô gợi ý (gom các tin cùng một lần gọi thuật toán; UUID). */
    @Column(name = "batch_id", nullable = false, columnDefinition = "CHAR(36)")
    private String batchId;

    /** Điểm tổng hợp xếp hạng. */
    @Column(name = "score", nullable = false, precision = 6, scale = 4)
    private BigDecimal score;

    /** Vị trí xếp hạng trong lô (>= 1). */
    @Column(name = "rank_position", nullable = false)
    private Integer rankPosition;

    /** Điểm thành phần: tương đồng diện tích. */
    @Column(name = "area_score", precision = 5, scale = 4)
    private BigDecimal areaScore;

    /** Điểm thành phần: tương đồng giá. */
    @Column(name = "price_score", precision = 5, scale = 4)
    private BigDecimal priceScore;

    /** Điểm thành phần: tương đồng loại hình. */
    @Column(name = "category_score", precision = 5, scale = 4)
    private BigDecimal categoryScore;

    /** Điểm thành phần: tương đồng tiện ích. */
    @Column(name = "amenity_score", precision = 5, scale = 4)
    private BigDecimal amenityScore;

    /** Điểm thành phần: điểm uy tín đã chuẩn hóa. */
    @Column(name = "trust_score_norm", precision = 5, scale = 4)
    private BigDecimal trustScoreNorm;

    /** Điểm thành phần: độ mới của tin. */
    @Column(name = "freshness_score", precision = 5, scale = 4)
    private BigDecimal freshnessScore;

    /** Hệ số đẩy tin quảng bá [1, 1.15]. */
    @Builder.Default
    @Column(name = "promoted_boost", nullable = false, precision = 4, scale = 3)
    private BigDecimal promotedBoost = BigDecimal.ONE;

    /** Gợi ý dạng cold-start (chưa đủ dữ liệu hành vi). */
    @Builder.Default
    @Column(name = "is_cold_start", nullable = false)
    private Boolean isColdStart = false;

    /** Ngữ cảnh gợi ý (JSON thô: bộ lọc, tin nguồn...). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context", columnDefinition = "json")
    private String context;

    /** Thời điểm người dùng bấm vào gợi ý (đo CTR); {@code null} nếu chưa bấm. */
    @Column(name = "clicked_at")
    private Instant clickedAt;

    /** Thời điểm ghi nhận (append-only). */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
