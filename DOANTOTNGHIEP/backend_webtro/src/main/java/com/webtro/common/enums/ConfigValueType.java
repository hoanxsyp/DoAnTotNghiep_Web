package com.webtro.common.enums;

/**
 * Kiểu giá trị của một config trong system_configs. SystemConfigService ép kiểu theo giá trị này.
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum ConfigValueType {

    /** Chuỗi */
    STRING("Chuỗi"),

    /** Số nguyên */
    INT("Số nguyên"),

    /** Số thực */
    DECIMAL("Số thực"),

    /** Luận lý */
    BOOLEAN("Luận lý"),

    /** JSON */
    JSON("JSON");

    private final String label;

    ConfigValueType(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
