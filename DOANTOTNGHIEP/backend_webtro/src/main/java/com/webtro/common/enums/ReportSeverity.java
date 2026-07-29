package com.webtro.common.enums;

/**
 * Mức độ nghiêm trọng của báo cáo [§6.3].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum ReportSeverity {

    /** Thấp */
    LOW("Thấp"),

    /** Trung bình */
    MEDIUM("Trung bình"),

    /** Cao */
    HIGH("Cao"),

    /** Nghiêm trọng */
    CRITICAL("Nghiêm trọng");

    private final String label;

    ReportSeverity(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
