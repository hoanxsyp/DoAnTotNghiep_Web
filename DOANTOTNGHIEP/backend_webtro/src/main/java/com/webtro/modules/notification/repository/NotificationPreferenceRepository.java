package com.webtro.modules.notification.repository;

import com.webtro.common.enums.NotificationType;
import com.webtro.modules.notification.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Truy vấn tùy chọn nhận thông báo. Bảng nghiệp vụ (xóa mềm) nên lọc {@code deleted_at IS NULL}
 * tường minh ở các truy vấn công khai.
 */
@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    /** Toàn bộ tùy chọn của một người dùng (dựng trang cài đặt). */
    List<NotificationPreference> findByUserIdAndDeletedAtIsNull(Long userId);

    /** Tùy chọn của người dùng cho một loại thông báo cụ thể. */
    Optional<NotificationPreference> findByUserIdAndNotificationTypeAndDeletedAtIsNull(
            Long userId, NotificationType notificationType);

    /** Kiểm tra người dùng đã có tùy chọn cho loại thông báo này chưa (chống tạo trùng). */
    boolean existsByUserIdAndNotificationTypeAndDeletedAtIsNull(
            Long userId, NotificationType notificationType);
}
