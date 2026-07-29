package com.webtro.common.enums;

/**
 * Kênh gửi thông báo [§5.6].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum NotificationChannel {

    /** Trong hệ thống */
    IN_APP("Trong hệ thống"),

    /** Email */
    EMAIL("Email");

    private final String label;

    NotificationChannel(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
