package com.webtro.common.enums;

/**
 * Đề xuất hành động sau phân tích cảm xúc [§9.1].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum SentimentAction {

    /** Không hành động */
    NONE("Không hành động"),

    /** Theo dõi */
    WATCH("Theo dõi"),

    /** Cần kiểm tra */
    NEED_REVIEW("Cần kiểm tra");

    private final String label;

    SentimentAction(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
