package com.webtro.common.enums;

/**
 * Ai thực hiện hành động kiểm duyệt. SYSTEM khi tự động ẩn theo [§5.3].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum ModerationActorType {

    /** Người thực hiện */
    USER("Người thực hiện"),

    /** Hệ thống tự động */
    SYSTEM("Hệ thống tự động");

    private final String label;

    ModerationActorType(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
