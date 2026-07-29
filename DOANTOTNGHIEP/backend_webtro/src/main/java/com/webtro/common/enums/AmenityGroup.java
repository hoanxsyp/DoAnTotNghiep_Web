package com.webtro.common.enums;

/**
 * Nhóm tiện ích [§10.5].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum AmenityGroup {

    /** Nội thất */
    FURNITURE("Nội thất"),

    /** An ninh */
    SECURITY("An ninh"),

    /** Sinh hoạt */
    UTILITY("Sinh hoạt"),

    /** Giao thông */
    TRANSPORT("Giao thông");

    private final String label;

    AmenityGroup(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
