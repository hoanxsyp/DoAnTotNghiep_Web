package com.webtro.common.enums;

/**
 * Tiêu chí gom nhóm báo cáo cho admin [§10.8]. Chỉ là tham số truy vấn, không lưu DB.
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum ReportGroupBy {

    /** Theo tin */
    LISTING("Theo tin"),

    /** Theo người dùng */
    USER("Theo người dùng"),

    /** Theo lý do */
    REASON("Theo lý do");

    private final String label;

    ReportGroupBy(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
