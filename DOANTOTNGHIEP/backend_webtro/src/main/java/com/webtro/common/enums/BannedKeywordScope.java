package com.webtro.common.enums;

/**
 * Phạm vi áp dụng từ khóa cấm [§3.3][§3.11].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum BannedKeywordScope {

    /** Chỉ tin đăng */
    LISTING("Chỉ tin đăng"),

    /** Chỉ bình luận */
    COMMENT("Chỉ bình luận"),

    /** Cả hai */
    BOTH("Cả hai");

    private final String label;

    BannedKeywordScope(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
