package com.webtro.common.enums;

/**
 * Nhãn cảm xúc bình luận [§9.1]. PENDING_ANALYSIS khi AI lỗi/timeout.
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum SentimentLabel {

    /** Tích cực */
    POSITIVE("Tích cực"),

    /** Trung lập */
    NEUTRAL("Trung lập"),

    /** Tiêu cực */
    NEGATIVE("Tiêu cực"),

    /** Vừa khen vừa chê */
    MIXED("Vừa khen vừa chê"),

    /** Chờ phân tích */
    PENDING_ANALYSIS("Chờ phân tích");

    private final String label;

    SentimentLabel(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
