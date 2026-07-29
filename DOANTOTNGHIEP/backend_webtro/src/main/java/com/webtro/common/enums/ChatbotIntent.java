package com.webtro.common.enums;

/**
 * Ý định người dùng trong chatbot [§9.3].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum ChatbotIntent {

    /** Tìm phòng */
    FIND_ROOM("Tìm phòng"),

    /** Hướng dẫn đăng tin */
    HOW_TO_POST("Hướng dẫn đăng tin"),

    /** Giải thích thuật ngữ */
    GLOSSARY("Giải thích thuật ngữ"),

    /** Câu hỏi thường gặp */
    FAQ("Câu hỏi thường gặp"),

    /** Chào hỏi */
    GREETING("Chào hỏi"),

    /** Ngoài phạm vi */
    OUT_OF_SCOPE("Ngoài phạm vi"),

    /** Nội dung nhạy cảm */
    SENSITIVE("Nội dung nhạy cảm"),

    /** Không xác định */
    UNKNOWN("Không xác định");

    private final String label;

    ChatbotIntent(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
