package com.webtro.common.enums;

/**
 * Loại tác nhân trong audit log [§11.4].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum AuditActorType {

    /** Người dùng */
    USER("Người dùng"),

    /** Hệ thống */
    SYSTEM("Hệ thống");

    private final String label;

    AuditActorType(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
