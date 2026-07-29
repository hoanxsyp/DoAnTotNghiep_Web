package com.webtro.common.enums;

/**
 * Quy định giờ giấc [§3.7].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum CurfewType {

    /** Giờ giấc tự do */
    FREE("Giờ giấc tự do"),

    /** Có giờ giới nghiêm */
    CURFEW("Có giờ giới nghiêm"),

    /** Không rõ */
    UNKNOWN("Không rõ");

    private final String label;

    CurfewType(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
