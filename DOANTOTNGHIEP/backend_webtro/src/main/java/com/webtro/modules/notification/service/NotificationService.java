package com.webtro.modules.notification.service;

import com.webtro.common.enums.NotificationType;

import java.util.Map;

/**
 * Gửi thông báo trong hệ thống + email (canonical mục 11, §5.6). Các module khác gọi qua interface
 * này (canonical luật 4), không đụng repository của module notification.
 *
 * <p>Tôn trọng {@code notification_preferences} {@code [§11.12]}: người dùng có thể tắt các loại
 * không bắt buộc. Các loại bắt buộc (khóa tài khoản, thanh toán thất bại...) luôn gửi.
 */
public interface NotificationService {

    /**
     * Tạo một thông báo in-app cho người dùng, và gửi email nếu loại đó bật kênh EMAIL và người
     * dùng không tắt.
     *
     * @param userId    người nhận
     * @param type      loại thông báo
     * @param title     tiêu đề
     * @param content   nội dung
     * @param actionUrl đường dẫn khi bấm vào (có thể null)
     * @param metadata  dữ liệu kèm theo để render (có thể null)
     */
    void notifyUser(Long userId, NotificationType type, String title, String content,
                    String actionUrl, Map<String, Object> metadata);

    /** Tiện dụng: không có actionUrl/metadata. */
    void notifyUser(Long userId, NotificationType type, String title, String content);

    /** Thông báo cho toàn bộ Admin/Moderator (cảnh báo AI, report vượt ngưỡng...). */
    void notifyModerators(NotificationType type, String title, String content, String actionUrl);
}
