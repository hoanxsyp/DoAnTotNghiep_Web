package com.webtro.modules.notification.entity;

import com.webtro.common.AuditableEntity;
import com.webtro.common.enums.NotificationChannel;
import com.webtro.common.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Thông báo gửi tới người dùng (in-app hoặc email) [§5.6][§9.2].
 *
 * <p>Bảng nghiệp vụ (có xóa mềm). Mỗi bản ghi là một thông báo cụ thể cho một người dùng.
 *
 * <p>Khóa liên module {@code user_id} (-> users.id) và tham chiếu đa hình
 * {@code ref_type}/{@code ref_id} chỉ giữ dạng nguyên thủy theo luật ranh giới module
 * (canonical luật 8): không map quan hệ {@code @ManyToOne} sang module khác.
 */
@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(name = "idx_notifications_user_id_is_read_created_at", columnList = "user_id, is_read, created_at"),
                @Index(name = "idx_notifications_channel_email_sent_at", columnList = "channel, email_sent_at"),
                @Index(name = "idx_notifications_type_created_at", columnList = "type, created_at"),
                @Index(name = "idx_notifications_ref", columnList = "ref_type, ref_id"),
                @Index(name = "idx_notifications_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Notification extends AuditableEntity {

    /** Người nhận thông báo (users.id). Khóa liên module -> giữ Long. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Loại thông báo. */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private NotificationType type;

    /** Kênh gửi (mặc định IN_APP). */
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 10)
    private NotificationChannel channel;

    /** Tiêu đề thông báo. */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** Nội dung thông báo. */
    @Column(name = "content", nullable = false, length = 1000)
    private String content;

    /** Đường dẫn điều hướng khi người dùng bấm vào thông báo. */
    @Column(name = "link", length = 500)
    private String link;

    /** Loại đối tượng tham chiếu (vd LISTING, PAYMENT...). Đi kèm ref_id (cùng null hoặc cùng khác null). */
    @Column(name = "ref_type", length = 20)
    private String refType;

    /** Id đối tượng tham chiếu (theo ref_type). Khóa đa hình liên module -> giữ Long. */
    @Column(name = "ref_id")
    private Long refId;

    /** Đã đọc hay chưa. */
    @Column(name = "is_read", nullable = false)
    private Boolean isRead;

    /** Thời điểm đọc (null = chưa đọc). Bắt buộc khác null khi is_read = true. */
    @Column(name = "read_at")
    private Instant readAt;

    /** Thời điểm gửi email thành công (chỉ với channel = EMAIL). */
    @Column(name = "email_sent_at")
    private Instant emailSentAt;

    /** Thông báo lỗi khi gửi email thất bại. */
    @Column(name = "email_error", length = 500)
    private String emailError;
}
