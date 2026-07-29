package com.webtro.common.enums;

/**
 * Mã loại tin đăng [§0.3]. Trùng khớp seed categories.
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum CategoryCode {

    /** Phòng trọ */
    BOARDING_HOUSE("Phòng trọ"),

    /** Chung cư mini */
    MINI_APARTMENT("Chung cư mini"),

    /** Căn hộ */
    APARTMENT("Căn hộ"),

    /** Nhà nguyên căn */
    WHOLE_HOUSE("Nhà nguyên căn"),

    /** Homestay cho thuê */
    HOMESTAY("Homestay cho thuê"),

    /** Ở ghép */
    ROOMMATE("Ở ghép"),

    /** Mặt bằng nhỏ */
    SMALL_PREMISES("Mặt bằng nhỏ");

    private final String label;

    CategoryCode(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
