package com.webtro.common.enums;

/**
 * Mức độ từ khóa cấm [§5.3].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum BannedKeywordSeverity {

    /** Nhẹ */
    MILD("Nhẹ"),

    /** Nghiêm trọng */
    SEVERE("Nghiêm trọng");

    private final String label;

    BannedKeywordSeverity(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
