package com.webtro.modules.notification.service.impl;

import com.webtro.common.enums.NotificationChannel;
import com.webtro.common.enums.NotificationType;
import com.webtro.common.mail.MailService;
import com.webtro.config.properties.AppProperties;
import com.webtro.constant.RoleCode;
import com.webtro.modules.notification.entity.Notification;
import com.webtro.modules.notification.entity.NotificationPreference;
import com.webtro.modules.notification.repository.NotificationPreferenceRepository;
import com.webtro.modules.notification.repository.NotificationRepository;
import com.webtro.modules.notification.service.NotificationDefaults;
import com.webtro.modules.notification.service.NotificationService;
import com.webtro.modules.user.entity.User;
import com.webtro.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cài đặt {@link NotificationService}. Tạo bản ghi in-app và (nếu được phép) gửi email bất đồng bộ.
 *
 * <p>Tôn trọng {@code notification_preferences} {@code [§11.12]}: bảng chỉ lưu NGOẠI LỆ (người dùng
 * tắt loại nào thì mới có bản ghi); vắng bản ghi nghĩa là bật mặc định. Các loại BẮT BUỘC (bảo mật,
 * tài chính) luôn gửi bất kể tùy chọn.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final MailService mailService;
    private final AppProperties appProperties;

    @Override
    @Transactional
    public void notifyUser(Long userId, NotificationType type, String title, String content,
                           String actionUrl, Map<String, Object> metadata) {
        Preference pref = resolvePreference(userId, type);
        if (!pref.inApp && !pref.email) {
            return; // Người dùng tắt hoàn toàn loại này (và không bắt buộc).
        }

        NotificationChannel channel = pref.email ? NotificationChannel.EMAIL : NotificationChannel.IN_APP;
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .channel(channel)
                .title(title)
                .content(content)
                .link(actionUrl)
                .isRead(false)
                .build();
        notificationRepository.save(notification);

        if (pref.email) {
            sendEmail(userId, title, content, actionUrl);
        }
    }

    @Override
    public void notifyUser(Long userId, NotificationType type, String title, String content) {
        notifyUser(userId, type, title, content, null, null);
    }

    @Override
    @Transactional
    public void notifyModerators(NotificationType type, String title, String content, String actionUrl) {
        // Người nhận: mọi tài khoản còn sống mang vai trò ADMIN hoặc MODERATOR. Lọc ngay ở DB
        // (WHERE role.code IN (...)) thay vì nạp cả bảng rồi lọc trong bộ nhớ.
        List<Long> recipientIds = userRepository
                .findByRole_CodeInAndDeletedAtIsNull(List.of(RoleCode.ADMIN, RoleCode.MODERATOR))
                .stream()
                .map(User::getId)
                .toList();
        if (recipientIds.isEmpty()) {
            log.warn("notifyModerators: không có tài khoản ADMIN/MODERATOR nào, bỏ qua type={}", type);
            return;
        }

        for (Long userId : recipientIds) {
            notifyUser(userId, type, title, content, actionUrl, null);
        }
        log.debug("notifyModerators type={} title={} recipients={}", type, title, recipientIds.size());
    }

    // ==================================================================

    private record Preference(boolean inApp, boolean email) {
    }

    private Preference resolvePreference(Long userId, NotificationType type) {
        if (NotificationDefaults.isMandatory(type)) {
            // Bắt buộc: luôn in-app; email nếu loại này mặc định gửi email.
            return new Preference(true, NotificationDefaults.emailDefaultOn(type));
        }
        // Bảng lưu ngoại lệ: có bản ghi -> theo bản ghi; không -> mặc định bật.
        NotificationPreference saved = preferenceRepository
                .findByUserIdAndNotificationTypeAndDeletedAtIsNull(userId, type)
                .orElse(null);
        if (saved == null) {
            return new Preference(true, NotificationDefaults.emailDefaultOn(type));
        }
        return new Preference(Boolean.TRUE.equals(saved.getInApp()), Boolean.TRUE.equals(saved.getEmail()));
    }

    private void sendEmail(Long userId, String title, String content, String actionUrl) {
        userRepository.findByIdAndDeletedAtIsNull(userId).ifPresent(user -> {
            Map<String, Object> vars = new HashMap<>();
            vars.put("title", title);
            vars.put("content", content);
            if (actionUrl != null) {
                String base = appProperties.getFrontend().getBaseUrl();
                vars.put("actionUrl", actionUrl.startsWith("http") ? actionUrl : base + actionUrl);
                vars.put("actionLabel", "Xem chi tiết");
            }
            mailService.sendHtml(user.getEmail(), title, "notification", vars);
        });
    }
}
