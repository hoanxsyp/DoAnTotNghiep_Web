package com.webtro.common.enums;

/**
 * Giới tính người dùng.
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum Gender {

    /** Nam */
    MALE("Nam"),

    /** Nữ */
    FEMALE("Nữ"),

    /** Khác */
    OTHER("Khác"),

    /** Chưa xác định */
    UNKNOWN("Chưa xác định");

    private final String label;

    Gender(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
