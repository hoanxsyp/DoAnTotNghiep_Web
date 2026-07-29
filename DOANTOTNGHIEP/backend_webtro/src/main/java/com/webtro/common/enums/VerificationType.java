package com.webtro.common.enums;

/**
 * Loại xác thực [§6.1].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum VerificationType {

    /** Xác thực email */
    EMAIL("Xác thực email"),

    /** Xác thực số điện thoại */
    PHONE("Xác thực số điện thoại"),

    /** Xác thực chủ trọ */
    LANDLORD("Xác thực chủ trọ");

    private final String label;

    VerificationType(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
