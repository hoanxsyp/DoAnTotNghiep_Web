package com.webtro.common.enums;

/**
 * Nguyên nhân tin bị hệ thống tự động ẩn [§5.3].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum AutoHideReason {

    /** Vượt ngưỡng báo cáo */
    REPORT_THRESHOLD("Vượt ngưỡng báo cáo"),

    /** Tỷ lệ tiêu cực cao */
    SENTIMENT_NEGATIVE("Tỷ lệ tiêu cực cao"),

    /** Chứa từ khóa cấm */
    BANNED_KEYWORD("Chứa từ khóa cấm");

    private final String label;

    AutoHideReason(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
