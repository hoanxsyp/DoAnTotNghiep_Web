package com.webtro.modules.moderation.entity;

import com.webtro.common.enums.ReportSeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Cảnh báo vi phạm gửi tới người dùng (append-only): lưu lịch sử các lần bị cảnh cáo, phục vụ
 * tính điểm/ngưỡng khóa tài khoản.
 *
 * <p><b>Không kế thừa {@code BaseEntity}/{@code AuditableEntity}</b>: bảng chỉ có {@code id} và
 * {@code created_at} (không có {@code updated_at}, không xóa mềm) nên khai báo tại chỗ để khớp
 * ddl-auto=validate.
 *
 * <p>Khóa liên module ({@code user_id}, {@code listing_id}, {@code issued_by} -> users/listings)
 * và {@code report_id}/{@code moderation_action_id} đều giữ dạng {@code Long}.
 */
@Entity
@Table(
        name = "violation_warnings",
        indexes = {
                @Index(name = "idx_violation_warnings_user_id_created_at", columnList = "user_id, created_at"),
                @Index(name = "idx_violation_warnings_listing_id", columnList = "listing_id"),
                @Index(name = "idx_violation_warnings_report_id", columnList = "report_id"),
                @Index(name = "idx_violation_warnings_issued_by", columnList = "issued_by"),
                @Index(name = "idx_violation_warnings_moderation_action_id", columnList = "moderation_action_id")
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ViolationWarning {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /** Người dùng bị cảnh báo (users.id). Khóa liên module -> giữ Long. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Tin đăng liên quan (listings.id). Khóa liên module -> giữ Long. */
    @Column(name = "listing_id")
    private Long listingId;

    /** Báo cáo nguồn (reports.id). */
    @Column(name = "report_id")
    private Long reportId;

    /** Hành động kiểm duyệt nguồn (moderation_actions.id). */
    @Column(name = "moderation_action_id")
    private Long moderationActionId;

    /** Mức độ nghiêm trọng của cảnh báo. */
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 10)
    private ReportSeverity severity;

    /** Lý do cảnh báo (ngắn gọn). */
    @Column(name = "reason", nullable = false, length = 200)
    private String reason;

    /** Nội dung cảnh báo gửi người dùng. */
    @Column(name = "content", nullable = false, length = 1000)
    private String content;

    /** Người ra cảnh báo (users.id). Null khi is_system = true. Khóa liên module -> giữ Long. */
    @Column(name = "issued_by")
    private Long issuedBy;

    /** Cảnh báo do hệ thống tự động phát. */
    @Column(name = "is_system", nullable = false)
    private Boolean isSystem;

    /** Thời điểm người dùng xác nhận đã đọc (null = chưa đọc). */
    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    /** Thời điểm phát cảnh báo (append-only). */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
