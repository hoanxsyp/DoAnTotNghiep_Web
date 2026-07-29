package com.webtro.common.enums;

/**
 * Loại đối tượng bị báo cáo [§2.8].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum ReportTargetType {

    /** Tin đăng */
    LISTING("Tin đăng"),

    /** Bình luận */
    COMMENT("Bình luận"),

    /** Người dùng */
    USER("Người dùng"),

    /** Đánh giá */
    REVIEW("Đánh giá");

    private final String label;

    ReportTargetType(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
