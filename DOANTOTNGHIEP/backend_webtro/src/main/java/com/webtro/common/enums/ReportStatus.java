package com.webtro.common.enums;

/**
 * Trạng thái báo cáo [§6.3].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum ReportStatus {

    /** Chờ xử lý */
    PENDING("Chờ xử lý"),

    /** Đang xử lý */
    REVIEWING("Đang xử lý"),

    /** Đã xử lý */
    RESOLVED("Đã xử lý"),

    /** Từ chối */
    REJECTED("Từ chối");

    private final String label;

    ReportStatus(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
