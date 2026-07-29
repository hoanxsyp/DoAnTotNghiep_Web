package com.webtro.common.enums;

/**
 * Loại giảm giá của mã khuyến mãi [§10.6].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum CouponDiscountType {

    /** Giảm theo phần trăm */
    PERCENT("Giảm theo phần trăm"),

    /** Giảm số tiền cố định */
    FIXED("Giảm số tiền cố định");

    private final String label;

    CouponDiscountType(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
