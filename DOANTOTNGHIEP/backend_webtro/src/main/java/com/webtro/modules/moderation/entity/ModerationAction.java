package com.webtro.modules.moderation.entity;

import com.webtro.common.enums.ModerationActionType;
import com.webtro.common.enums.ModerationResult;
import com.webtro.common.enums.ReportTargetType;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.EntityListeners;
import java.time.Instant;

/**
 * Nhật ký hành động kiểm duyệt (append-only): mỗi lần admin/hệ thống thao tác trên một đối tượng
 * (duyệt, ẩn, khóa, cảnh báo...) đều ghi một bản ghi bất biến.
 *
 * <p><b>Không kế thừa {@code BaseEntity}/{@code AuditableEntity}</b>: bảng chỉ có {@code id} và
 * {@code created_at} (không có {@code updated_at}, không xóa mềm), nên nếu kế thừa sẽ ánh xạ cột
 * không tồn tại và fail ở ddl-auto=validate. Vì vậy khai báo {@code id}/{@code created_at} tại chỗ.
 *
 * <p>Các khóa liên module ({@code moderator_id}, {@code listing_id} -> users/listings) và cả
 * {@code report_id} đều giữ dạng {@code Long}.
 */
@Entity
@Table(
        name = "moderation_actions",
        indexes = {
                @Index(name = "idx_moderation_actions_listing_id_action_created", columnList = "listing_id, action_type, created_at"),
                @Index(name = "idx_moderation_actions_report_id", columnList = "report_id"),
                @Index(name = "idx_moderation_actions_moderator_id_created_at", columnList = "moderator_id, created_at"),
                @Index(name = "idx_moderation_actions_target", columnList = "target_type, target_id, created_at")
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModerationAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /** Người kiểm duyệt (users.id). Null khi is_system = true. Khóa liên module -> giữ Long. */
    @Column(name = "moderator_id")
    private Long moderatorId;

    /** Hành động do hệ thống tự động thực hiện. */
    @Column(name = "is_system", nullable = false)
    private Boolean isSystem;

    /** Báo cáo liên quan (reports.id), nếu hành động xuất phát từ một báo cáo. */
    @Column(name = "report_id")
    private Long reportId;

    /** Loại đối tượng bị tác động. */
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 10)
    private ReportTargetType targetType;

    /** Id đối tượng bị tác động (theo target_type). */
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    /** Tin đăng liên quan (listings.id). Khóa liên module -> giữ Long. */
    @Column(name = "listing_id")
    private Long listingId;

    /** Loại hành động kiểm duyệt. */
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private ModerationActionType actionType;

    /** Kết luận vi phạm (nullable). */
    @Enumerated(EnumType.STRING)
    @Column(name = "result", length = 15)
    private ModerationResult result;

    /** Lý do hành động (bắt buộc). */
    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    /** Ghi chú thêm. */
    @Column(name = "note", length = 1000)
    private String note;

    /** Trạng thái đối tượng trước hành động (chuỗi tự do theo từng loại đối tượng). */
    @Column(name = "previous_status", length = 20)
    private String previousStatus;

    /** Trạng thái đối tượng sau hành động. */
    @Column(name = "new_status", length = 20)
    private String newStatus;

    /** Thời điểm ghi nhận (append-only). */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
