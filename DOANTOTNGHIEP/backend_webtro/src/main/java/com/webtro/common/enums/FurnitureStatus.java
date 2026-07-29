package com.webtro.common.enums;

/**
 * Tình trạng nội thất [§3.7].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum FurnitureStatus {

    /** Không nội thất */
    NONE("Không nội thất"),

    /** Nội thất cơ bản */
    BASIC("Nội thất cơ bản"),

    /** Nội thất đầy đủ */
    FULL("Nội thất đầy đủ");

    private final String label;

    FurnitureStatus(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
