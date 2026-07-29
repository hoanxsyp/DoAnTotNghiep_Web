package com.webtro.modules.admin.entity;

import com.webtro.common.enums.AuditAction;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Nhật ký kiểm toán (append-only) [§11.4]: mỗi hành động quản trị nhạy cảm (khóa user, đổi vai trò,
 * duyệt/khóa tin, đổi cấu hình AI/hệ thống, hoàn tiền...) đều ghi một bản ghi bất biến.
 *
 * <p><b>Không kế thừa {@code BaseEntity}/{@code AuditableEntity}</b>: bảng chỉ có {@code id} và
 * {@code created_at} (không có {@code updated_at}, không có {@code created_by}/{@code updated_by},
 * không xóa mềm). Nếu kế thừa sẽ ánh xạ cột không tồn tại và fail ở {@code ddl-auto=validate}, nên
 * khai báo {@code id}/{@code created_at} tại chỗ.
 *
 * <p>Khóa liên module ({@code actor_id} -> users.id, {@code target_id} -> đối tượng bị tác động)
 * giữ dạng {@code Long}, không map quan hệ (canonical luật 8).
 */
@Entity
@Table(
        name = "audit_logs",
        indexes = {
                @Index(name = "idx_audit_logs_actor_id_created_at", columnList = "actor_id, created_at"),
                @Index(name = "idx_audit_logs_target", columnList = "target_type, target_id, created_at"),
                @Index(name = "idx_audit_logs_action_created_at", columnList = "action, created_at"),
                @Index(name = "idx_audit_logs_created_at", columnList = "created_at"),
                @Index(name = "idx_audit_logs_request_id", columnList = "request_id")
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /** Người thực hiện hành động (users.id). Null nếu do hệ thống hoặc user đã bị xóa. Khóa liên module -> Long. */
    @Column(name = "actor_id")
    private Long actorId;

    /** Email của actor tại thời điểm ghi (snapshot, giữ lại kể cả khi user bị xóa). */
    @Column(name = "actor_email", length = 190)
    private String actorEmail;

    /** Loại hành động. */
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 25)
    private AuditAction action;

    /** Loại đối tượng bị tác động (USER, LISTING, PAYMENT, AI_CONFIG, SYSTEM_CONFIG...). Chuỗi tự do. */
    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    /** Id đối tượng bị tác động (theo target_type). Khóa liên module -> Long. */
    @Column(name = "target_id")
    private Long targetId;

    /** Nhãn mô tả đối tượng bị tác động (snapshot để hiển thị nhanh). */
    @Column(name = "target_label", length = 200)
    private String targetLabel;

    /** Giá trị trước thay đổi, lưu JSON thô. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value", columnDefinition = "json")
    private String oldValue;

    /** Giá trị sau thay đổi, lưu JSON thô. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", columnDefinition = "json")
    private String newValue;

    /** Lý do/ghi chú hành động. */
    @Column(name = "reason", length = 500)
    private String reason;

    /** Địa chỉ IP của actor (IPv4/IPv6). */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /** User-Agent của actor. */
    @Column(name = "user_agent", length = 255)
    private String userAgent;

    /** Mã request (trace id) để đối chiếu log. */
    @Column(name = "request_id", columnDefinition = "CHAR(36)")
    private String requestId;

    /** Thời điểm ghi nhận (append-only). */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
