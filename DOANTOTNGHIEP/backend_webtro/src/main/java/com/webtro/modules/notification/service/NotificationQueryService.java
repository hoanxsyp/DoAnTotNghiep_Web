package com.webtro.modules.notification.service;

import com.webtro.common.enums.NotificationType;
import com.webtro.modules.notification.dto.request.UpdatePreferenceRequest;
import com.webtro.modules.notification.dto.response.MarkAllReadResponse;
import com.webtro.modules.notification.dto.response.MarkReadResponse;
import com.webtro.modules.notification.dto.response.NotificationListResponse;
import com.webtro.modules.notification.dto.response.NotificationPreferencesResponse;
import com.webtro.modules.notification.dto.response.UnreadCountResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Thao tác đọc/đánh dấu/cài đặt thông báo cho người dùng đang đăng nhập
 * (NOTI-01→06, docs/03 mục 4.10). Tách khỏi {@link NotificationService} (chuyên gửi) để giữ
 * ranh giới đọc/ghi rõ ràng.
 */
public interface NotificationQueryService {

    /**
     * Danh sách thông báo của một người dùng, lọc theo loại và trạng thái chưa đọc.
     *
     * @param userId     người dùng
     * @param types      lọc theo tập loại (null/rỗng = tất cả)
     * @param unreadOnly chỉ lấy chưa đọc
     * @param pageable   phân trang (chỉ cho phép sort theo {@code createdAt})
     */
    NotificationListResponse list(Long userId, List<NotificationType> types, boolean unreadOnly,
                                  Pageable pageable);

    /** Số thông báo và tin nhắn chưa đọc (badge). */
    UnreadCountResponse unreadCount(Long userId);

    /** Đánh dấu đã đọc một thông báo (idempotent); trả trạng thái + số chưa đọc còn lại. */
    MarkReadResponse markRead(Long userId, Long notificationId);

    /** Đánh dấu đã đọc tất cả (hoặc một loại nếu {@code type} khác null). */
    MarkAllReadResponse markAllRead(Long userId, NotificationType type);

    /** Xóa mềm một thông báo của người dùng. */
    void delete(Long userId, Long notificationId);

    /** Toàn bộ tùy chọn nhận thông báo (16 loại) của người dùng. */
    NotificationPreferencesResponse getPreferences(Long userId);

    /** Cập nhật tùy chọn; từ chối tắt loại bắt buộc. Trả về danh sách tùy chọn sau cập nhật. */
    NotificationPreferencesResponse updatePreferences(Long userId, UpdatePreferenceRequest request);
}
