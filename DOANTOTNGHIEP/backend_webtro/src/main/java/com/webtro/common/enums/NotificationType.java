package com.webtro.common.enums;

/**
 * Loại thông báo [§5.6][§9.2].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum NotificationType {

    /** Đăng ký thành công */
    ACCOUNT_REGISTERED("Đăng ký thành công"),

    /** Tin được duyệt */
    LISTING_APPROVED("Tin được duyệt"),

    /** Tin bị từ chối */
    LISTING_REJECTED("Tin bị từ chối"),

    /** Tin sắp hết hạn */
    LISTING_EXPIRING("Tin sắp hết hạn"),

    /** Tin đã hết hạn */
    LISTING_EXPIRED("Tin đã hết hạn"),

    /** Tin bị khóa */
    LISTING_LOCKED("Tin bị khóa"),

    /** Tin bị ẩn */
    LISTING_HIDDEN("Tin bị ẩn"),

    /** Có người liên hệ */
    NEW_CONTACT("Có người liên hệ"),

    /** Có bình luận mới */
    NEW_COMMENT("Có bình luận mới"),

    /** Có đánh giá mới */
    NEW_REVIEW("Có đánh giá mới"),

    /** Thanh toán thành công */
    PAYMENT_SUCCESS("Thanh toán thành công"),

    /** Thanh toán thất bại */
    PAYMENT_FAILED("Thanh toán thất bại"),

    /** Tin bị báo cáo nhiều */
    REPORT_THRESHOLD("Tin bị báo cáo nhiều"),

    /** AI cảnh báo tiêu cực */
    AI_NEGATIVE_ALERT("AI cảnh báo tiêu cực"),

    /** Tài khoản bị khóa */
    ACCOUNT_LOCKED("Tài khoản bị khóa"),

    /** Cảnh báo vi phạm */
    VIOLATION_WARNING("Cảnh báo vi phạm"),

    /** Cập nhật trạng thái xác thực chủ trọ */
    ACCOUNT_VERIFICATION("Cập nhật trạng thái xác thực chủ trọ"),

    /** Chủ trọ theo dõi có tin mới */
    FOLLOWED_LANDLORD_NEW_LISTING("Chủ trọ theo dõi có tin mới"),

    /** Có tin mới phù hợp */
    NEW_MATCHING_LISTING("Có tin mới phù hợp");

    private final String label;

    NotificationType(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
