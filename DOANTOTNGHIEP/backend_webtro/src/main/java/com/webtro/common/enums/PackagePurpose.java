package com.webtro.common.enums;

/**
 * Mục đích gói dịch vụ [§2.9].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum PackagePurpose {

    /** Đẩy lên đầu */
    PUSH_TOP("Đẩy lên đầu"),

    /** Gắn nhãn nổi bật */
    HIGHLIGHT("Gắn nhãn nổi bật"),

    /** Cả hai */
    BOTH("Cả hai");

    private final String label;

    PackagePurpose(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
