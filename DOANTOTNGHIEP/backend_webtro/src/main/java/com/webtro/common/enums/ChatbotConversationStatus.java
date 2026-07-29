package com.webtro.common.enums;

/**
 * Trạng thái phiên chatbot [§9.3].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum ChatbotConversationStatus {

    /** Đang trò chuyện */
    ACTIVE("Đang trò chuyện"),

    /** Đã hoàn tất */
    COMPLETED("Đã hoàn tất"),

    /** Bỏ dở */
    ABANDONED("Bỏ dở");

    private final String label;

    ChatbotConversationStatus(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
