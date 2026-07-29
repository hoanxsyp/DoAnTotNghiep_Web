package com.webtro.common.enums;

/**
 * Nhãn uy tín suy ra từ trust_score [§5.8]. Không lưu cột, tính tại runtime.
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum TrustLabel {

    /** Tốt */
    GOOD("Tốt"),

    /** Bình thường */
    NORMAL("Bình thường"),

    /** Rủi ro */
    RISKY("Rủi ro"),

    /** Cần kiểm duyệt */
    NEED_REVIEW("Cần kiểm duyệt");

    private final String label;

    TrustLabel(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
