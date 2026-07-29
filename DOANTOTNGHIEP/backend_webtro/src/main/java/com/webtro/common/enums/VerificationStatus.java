package com.webtro.common.enums;

/**
 * Trạng thái xác thực.
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum VerificationStatus {

    /** Chờ xác thực */
    PENDING("Chờ xác thực"),

    /** Đã xác thực */
    VERIFIED("Đã xác thực"),

    /** Từ chối */
    REJECTED("Từ chối"),

    /** Hết hạn */
    EXPIRED("Hết hạn");

    private final String label;

    VerificationStatus(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
