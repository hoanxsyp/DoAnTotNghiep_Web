package com.webtro.common.enums;

/**
 * Kết quả xử lý kiểm duyệt [§10.8].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum ModerationResult {

    /** Không vi phạm */
    NO_VIOLATION("Không vi phạm"),

    /** Vi phạm nhẹ - nhắc nhở */
    MINOR_WARN("Vi phạm nhẹ - nhắc nhở"),

    /** Vi phạm trung bình - ẩn nội dung */
    MEDIUM_HIDE("Vi phạm trung bình - ẩn nội dung"),

    /** Vi phạm nặng - khóa */
    SEVERE_LOCK("Vi phạm nặng - khóa");

    private final String label;

    ModerationResult(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
