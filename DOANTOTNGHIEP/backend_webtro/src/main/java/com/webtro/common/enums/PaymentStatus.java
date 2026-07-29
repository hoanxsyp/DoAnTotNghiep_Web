package com.webtro.common.enums;

/**
 * Trạng thái giao dịch thanh toán [§6.3][§10.7].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum PaymentStatus {

    /** Chờ thanh toán */
    PENDING("Chờ thanh toán"),

    /** Thành công */
    SUCCESS("Thành công"),

    /** Thất bại */
    FAILED("Thất bại"),

    /** Đã hủy */
    CANCELLED("Đã hủy"),

    /** Đã hoàn tiền */
    REFUNDED("Đã hoàn tiền");

    private final String label;

    PaymentStatus(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
