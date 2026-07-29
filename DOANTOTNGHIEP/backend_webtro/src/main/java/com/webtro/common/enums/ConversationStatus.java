package com.webtro.common.enums;

/**
 * Trạng thái cuộc trò chuyện [§3.10].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum ConversationStatus {

    /** Đang hoạt động */
    ACTIVE("Đang hoạt động"),

    /** Đã lưu trữ */
    ARCHIVED("Đã lưu trữ"),

    /** Bị chặn */
    BLOCKED("Bị chặn");

    private final String label;

    ConversationStatus(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
