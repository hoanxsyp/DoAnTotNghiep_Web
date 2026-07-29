package com.webtro.common.enums;

/**
 * Trạng thái bình luận [§3.11].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum CommentStatus {

    /** Hiển thị */
    VISIBLE("Hiển thị"),

    /** Chờ duyệt */
    PENDING("Chờ duyệt"),

    /** Đã ẩn */
    HIDDEN("Đã ẩn"),

    /** Đã xóa */
    DELETED("Đã xóa");

    private final String label;

    CommentStatus(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
