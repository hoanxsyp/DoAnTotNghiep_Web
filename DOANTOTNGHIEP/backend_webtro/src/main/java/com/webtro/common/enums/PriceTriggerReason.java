package com.webtro.common.enums;

/**
 * Lý do kích hoạt tính lại giá dự đoán [§5.9].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum PriceTriggerReason {

    /** Tạo tin */
    CREATE("Tạo tin"),

    /** Sửa diện tích */
    EDIT_AREA("Sửa diện tích"),

    /** Sửa loại tin */
    EDIT_CATEGORY("Sửa loại tin"),

    /** Sửa khu vực */
    EDIT_LOCATION("Sửa khu vực"),

    /** Sửa tiện ích */
    EDIT_AMENITY("Sửa tiện ích"),

    /** Admin tính lại */
    ADMIN_RECALC("Admin tính lại");

    private final String label;

    PriceTriggerReason(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
