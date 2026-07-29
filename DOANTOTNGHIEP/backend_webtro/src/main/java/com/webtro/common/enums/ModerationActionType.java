package com.webtro.common.enums;

/**
 * Loại hành động kiểm duyệt [§4.4][§7.4].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum ModerationActionType {

    /** Duyệt */
    APPROVE("Duyệt"),

    /** Từ chối */
    REJECT("Từ chối"),

    /** Ẩn */
    HIDE("Ẩn"),

    /** Bỏ ẩn */
    UNHIDE("Bỏ ẩn"),

    /** Khóa */
    LOCK("Khóa"),

    /** Mở khóa */
    UNLOCK("Mở khóa"),

    /** Cảnh báo */
    WARN("Cảnh báo"),

    /** Yêu cầu sửa */
    REQUEST_EDIT("Yêu cầu sửa"),

    /** Đánh dấu cần kiểm tra */
    FLAG_NEED_REVIEW("Đánh dấu cần kiểm tra"),

    /** Bỏ đánh dấu cần kiểm tra */
    CLEAR_NEED_REVIEW("Bỏ đánh dấu cần kiểm tra"),

    /** Bỏ qua */
    DISMISS("Bỏ qua");

    private final String label;

    ModerationActionType(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
