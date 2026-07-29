package com.webtro.modules.notification.service;

import com.webtro.common.enums.NotificationType;

import java.util.List;
import java.util.Set;

/**
 * Chính sách mặc định cho thông báo — nguồn sự thật duy nhất dùng chung cho cả luồng GỬI
 * ({@code NotificationServiceImpl}) và luồng CÀI ĐẶT ({@code NotificationQueryServiceImpl}).
 *
 * <p>Ba tập bất biến (canonical §5.6, docs/03 mục 4.10.6):
 * <ul>
 *   <li>{@link #PREFERENCE_TYPES}: 16 loại được phép cấu hình (toàn bộ {@link NotificationType}
 *       trừ {@code NEW_MATCHING_LISTING} — DB {@code ck_notification_preferences_type} không nhận
 *       loại này).</li>
 *   <li>{@link #MANDATORY}: loại quan trọng, {@code optional = false}, người dùng KHÔNG tắt được
 *       (tắt → 422 {@code NOTIFICATION_TYPE_NOT_OPTIONAL}).</li>
 *   <li>{@link #EMAIL_DEFAULT_ON}: loại mặc định bật thêm kênh EMAIL (ngoài IN_APP luôn bật mặc
 *       định); các loại còn lại mặc định chỉ IN_APP.</li>
 * </ul>
 */
public final class NotificationDefaults {

    /**
     * 16 loại thông báo được phép lưu tùy chọn — theo đúng thứ tự hiển thị trang cài đặt.
     * KHÔNG chứa {@code NEW_MATCHING_LISTING} (ràng buộc DB {@code ck_notification_preferences_type}).
     */
    public static final List<NotificationType> PREFERENCE_TYPES = List.of(
            NotificationType.ACCOUNT_REGISTERED,
            NotificationType.LISTING_APPROVED,
            NotificationType.LISTING_REJECTED,
            NotificationType.LISTING_EXPIRING,
            NotificationType.LISTING_EXPIRED,
            NotificationType.LISTING_LOCKED,
            NotificationType.NEW_CONTACT,
            NotificationType.NEW_COMMENT,
            NotificationType.NEW_REVIEW,
            NotificationType.PAYMENT_SUCCESS,
            NotificationType.PAYMENT_FAILED,
            NotificationType.REPORT_THRESHOLD,
            NotificationType.AI_NEGATIVE_ALERT,
            NotificationType.ACCOUNT_LOCKED,
            NotificationType.VIOLATION_WARNING,
            NotificationType.FOLLOWED_LANDLORD_NEW_LISTING);

    /**
     * Loại BẮT BUỘC ({@code optional = false}) — docs/03 mục 4.10.6.
     * Người dùng không được tắt (cả in-app lẫn email luôn coi như bật).
     */
    public static final Set<NotificationType> MANDATORY = Set.of(
            NotificationType.ACCOUNT_REGISTERED,
            NotificationType.LISTING_APPROVED,
            NotificationType.LISTING_REJECTED,
            NotificationType.LISTING_LOCKED,
            NotificationType.PAYMENT_SUCCESS,
            NotificationType.PAYMENT_FAILED,
            NotificationType.ACCOUNT_LOCKED,
            NotificationType.VIOLATION_WARNING,
            NotificationType.REPORT_THRESHOLD,
            NotificationType.AI_NEGATIVE_ALERT,
            NotificationType.ACCOUNT_VERIFICATION);

    /**
     * Loại mặc định gửi kèm EMAIL (canonical §5.6). Toàn bộ loại bắt buộc + hai loại liên hệ/sắp
     * hết hạn. Các loại còn lại (bình luận, đánh giá, tin đã hết hạn, chủ trọ theo dõi có tin mới)
     * mặc định chỉ IN_APP.
     */
    public static final Set<NotificationType> EMAIL_DEFAULT_ON = Set.of(
            NotificationType.ACCOUNT_REGISTERED,
            NotificationType.LISTING_APPROVED,
            NotificationType.LISTING_REJECTED,
            NotificationType.LISTING_LOCKED,
            NotificationType.LISTING_EXPIRING,
            NotificationType.NEW_CONTACT,
            NotificationType.PAYMENT_SUCCESS,
            NotificationType.PAYMENT_FAILED,
            NotificationType.ACCOUNT_LOCKED,
            NotificationType.VIOLATION_WARNING,
            NotificationType.REPORT_THRESHOLD,
            NotificationType.AI_NEGATIVE_ALERT,
            NotificationType.ACCOUNT_VERIFICATION);

    private NotificationDefaults() {
    }

    /** Loại này có bắt buộc (không tắt được) không. */
    public static boolean isMandatory(NotificationType type) {
        return MANDATORY.contains(type);
    }

    /** Loại này mặc định có bật email không (khi người dùng chưa lưu tùy chọn riêng). */
    public static boolean emailDefaultOn(NotificationType type) {
        return EMAIL_DEFAULT_ON.contains(type);
    }

    /** Loại này có được phép lưu tùy chọn không (nằm trong tập DB cho phép). */
    public static boolean isConfigurable(NotificationType type) {
        return PREFERENCE_TYPES.contains(type);
    }
}
