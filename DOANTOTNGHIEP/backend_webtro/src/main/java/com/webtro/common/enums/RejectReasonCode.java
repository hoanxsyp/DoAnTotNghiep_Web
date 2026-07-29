package com.webtro.common.enums;

/**
 * Mã lý do từ chối tin [§3.3].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum RejectReasonCode {

    /** Thiếu thông tin */
    MISSING_INFO("Thiếu thông tin"),

    /** Giá không hợp lệ */
    WRONG_PRICE("Giá không hợp lệ"),

    /** Ảnh không thật */
    FAKE_IMAGE("Ảnh không thật"),

    /** Nội dung cấm */
    BANNED_CONTENT("Nội dung cấm"),

    /** Diện tích không hợp lệ */
    WRONG_AREA("Diện tích không hợp lệ"),

    /** Tin trùng */
    DUPLICATE("Tin trùng"),

    /** Khác */
    OTHER("Khác");

    private final String label;

    RejectReasonCode(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
