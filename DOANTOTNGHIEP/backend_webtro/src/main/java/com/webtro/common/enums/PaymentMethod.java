package com.webtro.common.enums;

/**
 * Phương thức thanh toán [§3.14].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum PaymentMethod {

    /** Cổng mô phỏng */
    SANDBOX("Cổng mô phỏng"),

    /** VNPay */
    VNPAY("VNPay"),

    /** MoMo */
    MOMO("MoMo"),

    /** Chuyển khoản */
    BANK_TRANSFER("Chuyển khoản");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
