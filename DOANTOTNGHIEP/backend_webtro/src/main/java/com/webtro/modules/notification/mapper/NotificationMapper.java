package com.webtro.modules.notification.mapper;

import com.webtro.common.enums.NotificationType;
import com.webtro.modules.notification.dto.response.NotificationPreferenceResponse;
import com.webtro.modules.notification.dto.response.NotificationResponse;
import com.webtro.modules.notification.entity.Notification;
import com.webtro.modules.notification.entity.NotificationPreference;
import com.webtro.modules.notification.service.NotificationDefaults;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * Chuyển đổi entity &lt;-&gt; DTO cho module notification (canonical luật 3 — nơi DUY NHẤT map).
 * Viết thủ công bằng Builder, không MapStruct.
 */
@Component
public class NotificationMapper {

    /** Loại có icon SUCCESS (kết quả tích cực). */
    private static final Set<NotificationType> ICON_SUCCESS = EnumSet.of(
            NotificationType.ACCOUNT_REGISTERED,
            NotificationType.LISTING_APPROVED,
            NotificationType.PAYMENT_SUCCESS);

    /** Loại có icon WARNING (cần chú ý). */
    private static final Set<NotificationType> ICON_WARNING = EnumSet.of(
            NotificationType.LISTING_EXPIRING,
            NotificationType.LISTING_EXPIRED,
            NotificationType.REPORT_THRESHOLD,
            NotificationType.AI_NEGATIVE_ALERT,
            NotificationType.VIOLATION_WARNING);

    /** Loại có icon ERROR (tiêu cực/chặn). */
    private static final Set<NotificationType> ICON_ERROR = EnumSet.of(
            NotificationType.LISTING_REJECTED,
            NotificationType.LISTING_LOCKED,
            NotificationType.PAYMENT_FAILED,
            NotificationType.ACCOUNT_LOCKED);

    /**
     * Map một thông báo sang DTO. {@code targetType/targetId/targetUrl} lấy từ
     * {@code refType/refId/link}; {@code read/readAt} từ {@code isRead/readAt}.
     */
    public NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .typeLabel(n.getType() != null ? n.getType().getLabel() : null)
                .title(n.getTitle())
                .content(n.getContent())
                .iconType(iconType(n.getType()))
                .targetType(n.getRefType())
                .targetId(n.getRefId())
                .targetUrl(n.getLink())
                .read(Boolean.TRUE.equals(n.getIsRead()))
                .readAt(n.getReadAt())
                .createdAt(n.getCreatedAt())
                .build();
    }

    /**
     * Dựng dòng cài đặt cho một loại. {@code saved} = bản ghi tùy chọn đã lưu (có thể null →
     * dùng mặc định: in-app bật, email theo {@link NotificationDefaults#emailDefaultOn}).
     * Loại bắt buộc luôn hiển thị bật cả hai kênh và {@code optional = false}.
     */
    public NotificationPreferenceResponse toPreferenceResponse(NotificationType type,
                                                               NotificationPreference saved) {
        boolean mandatory = NotificationDefaults.isMandatory(type);
        boolean inApp;
        boolean email;
        if (mandatory) {
            inApp = true;
            email = true;
        } else if (saved != null) {
            inApp = Boolean.TRUE.equals(saved.getInApp());
            email = Boolean.TRUE.equals(saved.getEmail());
        } else {
            inApp = true;
            email = NotificationDefaults.emailDefaultOn(type);
        }
        return NotificationPreferenceResponse.builder()
                .type(type)
                .typeLabel(type.getLabel())
                .inApp(inApp)
                .email(email)
                .optional(!mandatory)
                .build();
    }

    private String iconType(NotificationType type) {
        if (type == null) {
            return "INFO";
        }
        if (ICON_SUCCESS.contains(type)) {
            return "SUCCESS";
        }
        if (ICON_ERROR.contains(type)) {
            return "ERROR";
        }
        if (ICON_WARNING.contains(type)) {
            return "WARNING";
        }
        return "INFO";
    }
}
