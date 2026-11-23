package com.webtro.common.enums;

/**
 * Trạng thái của Phòng ({@code rental_units.status}).
 *
 * <p>Phòng không bao giờ bị xoá khi đã có đánh giá hoặc lịch sử thuê (RU-5): chủ trọ chỉ lưu trữ.
 */
public enum RentalUnitStatus {

    /** Đang dùng: đăng tin được. */
    ACTIVE("Đang dùng"),

    /** Đã lưu trữ: không đăng tin mới được, đánh giá cũ vẫn giữ. */
    ARCHIVED("Đã lưu trữ");

    private final String label;

    RentalUnitStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
