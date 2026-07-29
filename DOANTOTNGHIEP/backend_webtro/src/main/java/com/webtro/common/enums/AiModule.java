package com.webtro.common.enums;

/**
 * 4 module AI [§9].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum AiModule {

    /** Phân tích cảm xúc */
    SENTIMENT("Phân tích cảm xúc"),

    /** Gợi ý tin */
    RECOMMENDATION("Gợi ý tin"),

    /** Chatbot */
    CHATBOT("Chatbot"),

    /** Dự đoán giá */
    PRICE("Dự đoán giá");

    private final String label;

    AiModule(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
