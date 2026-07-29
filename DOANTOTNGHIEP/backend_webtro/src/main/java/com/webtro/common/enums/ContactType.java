package com.webtro.common.enums;

/**
 * Hình thức liên hệ [§3.10].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum ContactType {

    /** Xem số điện thoại */
    VIEW_PHONE("Xem số điện thoại"),

    /** Gửi form liên hệ */
    FORM("Gửi form liên hệ"),

    /** Chat nội bộ */
    CHAT("Chat nội bộ");

    private final String label;

    ContactType(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
