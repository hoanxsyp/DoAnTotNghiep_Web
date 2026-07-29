package com.webtro.common.enums;

/**
 * Loại đơn vị hành chính [§10.5].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum AdministrativeUnitType {

    /** Tỉnh/Thành */
    PROVINCE("Tỉnh/Thành"),

    /** Quận/Huyện */
    DISTRICT("Quận/Huyện"),

    /** Phường/Xã */
    WARD("Phường/Xã");

    private final String label;

    AdministrativeUnitType(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
