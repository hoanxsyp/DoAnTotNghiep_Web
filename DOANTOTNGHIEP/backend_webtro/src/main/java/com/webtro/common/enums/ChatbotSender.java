package com.webtro.common.enums;

/**
 * Người gửi tin nhắn chatbot [§9.3].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum ChatbotSender {

    /** Người dùng */
    USER("Người dùng"),

    /** Chatbot */
    BOT("Chatbot");

    private final String label;

    ChatbotSender(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
