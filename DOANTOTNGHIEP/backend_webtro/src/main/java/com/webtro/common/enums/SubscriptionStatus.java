package com.webtro.common.enums;

/**
 * Trạng thái gói đẩy tin đã mua.
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum SubscriptionStatus {

    /** Chờ kích hoạt */
    PENDING("Chờ kích hoạt"),

    /** Đang chạy */
    ACTIVE("Đang chạy"),

    /** Hết hạn */
    EXPIRED("Hết hạn"),

    /** Đã hủy */
    CANCELLED("Đã hủy");

    private final String label;

    SubscriptionStatus(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
