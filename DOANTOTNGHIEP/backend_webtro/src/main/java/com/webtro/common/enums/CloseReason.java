package com.webtro.common.enums;

/**
 * Lý do đóng tin [§3.6].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum CloseReason {

    /** Đã cho thuê */
    RENTED_OUT("Đã cho thuê"),

    /** Không còn nhu cầu */
    NO_LONGER_AVAILABLE("Không còn nhu cầu"),

    /** Khác */
    OTHER("Khác");

    private final String label;

    CloseReason(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
