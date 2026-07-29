package com.webtro.modules.moderation.entity;

import com.webtro.common.AuditableEntity;
import com.webtro.common.enums.ReportReason;
import com.webtro.common.enums.ReportSeverity;
import com.webtro.common.enums.ReportStatus;
import com.webtro.common.enums.ReportTargetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Báo cáo/tố cáo nội dung vi phạm do người dùng gửi (tin đăng, bình luận, người dùng, đánh giá).
 *
 * <p>Bảng nghiệp vụ (có xóa mềm). Chống trùng bằng {@code (reporter_id, dedup_key)}: một người
 * không thể báo cáo trùng cùng một đối tượng/lý do trong cửa sổ chống trùng.
 *
 * <p>Các khóa trỏ sang module khác ({@code reporter_id}, {@code listing_id}, {@code resolved_by}
 * -> users/listings) chỉ giữ dạng {@code Long} theo luật ranh giới module (canonical luật 8).
 */
@Entity
@Table(
        name = "reports",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_reports_reporter_dedup", columnNames = {"reporter_id", "dedup_key"})
        },
        indexes = {
                @Index(name = "idx_reports_status_severity_created_at", columnList = "status, severity, created_at"),
                @Index(name = "idx_reports_target", columnList = "target_type, target_id, created_at"),
                @Index(name = "idx_reports_listing_id_created_at", columnList = "listing_id, created_at"),
                @Index(name = "idx_reports_reporter_id_created_at", columnList = "reporter_id, created_at"),
                @Index(name = "idx_reports_is_valid", columnList = "is_valid, reporter_id"),
                @Index(name = "idx_reports_resolved_by", columnList = "resolved_by")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Report extends AuditableEntity {

    /** Người gửi báo cáo (users.id). Khóa liên module -> giữ Long. */
    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    /** Loại đối tượng bị báo cáo. */
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 10)
    private ReportTargetType targetType;

    /** Id của đối tượng bị báo cáo (theo target_type). Khóa đa hình -> giữ Long. */
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    /** Tin đăng liên quan (listings.id), phục vụ lọc/thống kê. Khóa liên module -> giữ Long. */
    @Column(name = "listing_id")
    private Long listingId;

    /** Lý do báo cáo. */
    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 20)
    private ReportReason reason;

    /** Mô tả chi tiết (bắt buộc khi reason = OTHER, ràng buộc ở DB). */
    @Column(name = "description", length = 1000)
    private String description;

    /** Ảnh bằng chứng. */
    @Column(name = "evidence_image_url", length = 500)
    private String evidenceImageUrl;

    /** Trạng thái xử lý. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ReportStatus status;

    /** Mức độ nghiêm trọng. */
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 10)
    private ReportSeverity severity;

    /** Kết luận báo cáo có hợp lệ hay không (null = chưa kết luận). */
    @Column(name = "is_valid")
    private Boolean isValid;

    /** Ghi chú khi xử lý xong. */
    @Column(name = "resolution_note", length = 1000)
    private String resolutionNote;

    /** Người xử lý (users.id). Khóa liên module -> giữ Long. */
    @Column(name = "resolved_by")
    private Long resolvedBy;

    /** Thời điểm xử lý xong. */
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /** Khóa chống trùng báo cáo (kết hợp reporter_id thành unique). */
    @Column(name = "dedup_key", nullable = false, length = 80)
    private String dedupKey;
}
