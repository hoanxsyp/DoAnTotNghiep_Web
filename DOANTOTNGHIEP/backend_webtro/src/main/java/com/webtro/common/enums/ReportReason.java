package com.webtro.common.enums;

/**
 * Lý do báo cáo [§3.13].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum ReportReason {

    /** Sai thông tin */
    WRONG_INFO("Sai thông tin"),

    /** Đã cho thuê */
    ALREADY_RENTED("Đã cho thuê"),

    /** Lừa đảo */
    SCAM("Lừa đảo"),

    /** Ảnh không thật */
    FAKE_IMAGE("Ảnh không thật"),

    /** Giá sai */
    WRONG_PRICE("Giá sai"),

    /** Nội dung phản cảm */
    OFFENSIVE("Nội dung phản cảm"),

    /** Spam */
    SPAM("Spam"),

    /** Khác */
    OTHER("Khác");

    private final String label;

    ReportReason(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
