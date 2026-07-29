package com.webtro.common.enums;

/**
 * Trạng thái tài khoản người dùng [§6.3].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum UserStatus {

    /** Đang hoạt động */
    ACTIVE("Đang hoạt động"),

    /** Chờ xác thực */
    PENDING_VERIFY("Chờ xác thực"),

    /** Bị khóa */
    LOCKED("Bị khóa"),

    /** Đã xóa */
    DELETED("Đã xóa");

    private final String label;

    UserStatus(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
