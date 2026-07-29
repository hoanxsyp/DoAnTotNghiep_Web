package com.webtro.modules.notification.entity;

import com.webtro.common.AuditableEntity;
import com.webtro.common.enums.NotificationType;
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

/**
 * Tùy chọn nhận thông báo của người dùng theo từng loại thông báo [§5.6].
 *
 * <p>Bảng nghiệp vụ (có xóa mềm). Mỗi (user_id, notification_type) là duy nhất
 * ({@code uk_notification_preferences_user_type}).
 *
 * <p>Với một số loại bắt buộc (đăng ký, duyệt/từ chối/khóa tin, thanh toán, khóa tài khoản,
 * cảnh báo vi phạm, báo cáo ngưỡng, AI cảnh báo) DB ràng buộc {@code in_app = TRUE AND email = TRUE}
 * (ck_notification_preferences_mandatory) — không cho người dùng tắt.
 *
 * <p>Khóa liên module {@code user_id} (-> users.id) chỉ giữ dạng Long (canonical luật 8).
 */
@Entity
@Table(
        name = "notification_preferences",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_notification_preferences_user_type", columnNames = {"user_id", "notification_type"})
        },
        indexes = {
                @Index(name = "idx_notification_preferences_user_id", columnList = "user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class NotificationPreference extends AuditableEntity {

    /** Người dùng sở hữu tùy chọn (users.id). Khóa liên module -> giữ Long. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * Loại thông báo áp dụng tùy chọn này.
     *
     * <p>Lưu ý: DB cho phép tập giá trị ít hơn {@link NotificationType} (không có NEW_MATCHING_LISTING
     * trong ck_notification_preferences_type). Tầng service phải chặn ghi giá trị ngoài tập cho phép.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 40)
    private NotificationType notificationType;

    /** Bật/tắt nhận thông báo trong ứng dụng. */
    @Column(name = "in_app", nullable = false)
    private Boolean inApp;

    /** Bật/tắt nhận thông báo qua email. */
    @Column(name = "email", nullable = false)
    private Boolean email;
}
