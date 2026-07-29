package com.webtro.common.enums;

/**
 * Trạng thái đánh giá.
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum ReviewStatus {

    /** Hiển thị */
    VISIBLE("Hiển thị"),

    /** Đã ẩn */
    HIDDEN("Đã ẩn"),

    /** Đã xóa */
    DELETED("Đã xóa");

    private final String label;

    ReviewStatus(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
