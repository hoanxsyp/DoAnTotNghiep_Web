package com.webtro.common.enums;

/**
 * Mức độ tin cậy của dự đoán giá [§9.4].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum PriceConfidence {

    /** Cao */
    HIGH("Cao"),

    /** Trung bình */
    MEDIUM("Trung bình"),

    /** Thấp */
    LOW("Thấp"),

    /** Không đủ dữ liệu */
    INSUFFICIENT_DATA("Không đủ dữ liệu");

    private final String label;

    PriceConfidence(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
