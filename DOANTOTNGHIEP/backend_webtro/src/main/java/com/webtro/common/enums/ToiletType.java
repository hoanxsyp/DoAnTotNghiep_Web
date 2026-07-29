package com.webtro.common.enums;

/**
 * Loại nhà vệ sinh [§3.7].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum ToiletType {

    /** Khép kín */
    PRIVATE("Khép kín"),

    /** Chung */
    SHARED("Chung");

    private final String label;

    ToiletType(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
