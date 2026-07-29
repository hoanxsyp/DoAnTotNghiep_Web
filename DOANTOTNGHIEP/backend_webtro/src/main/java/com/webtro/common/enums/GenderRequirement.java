package com.webtro.common.enums;

/**
 * Yêu cầu giới tính cho tin ở ghép [§0.3][§3.3].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum GenderRequirement {

    /** Chỉ nam */
    MALE_ONLY("Chỉ nam"),

    /** Chỉ nữ */
    FEMALE_ONLY("Chỉ nữ"),

    /** Nam hoặc nữ */
    ANY("Nam hoặc nữ");

    private final String label;

    GenderRequirement(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
