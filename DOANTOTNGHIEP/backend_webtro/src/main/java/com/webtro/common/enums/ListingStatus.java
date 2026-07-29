package com.webtro.common.enums;

/**
 * Trạng thái tin đăng [§0.4][§5.1]. Vòng đời điều khiển bởi ListingStateMachine.
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum ListingStatus {

    /** Nháp */
    DRAFT("Nháp"),

    /** Chờ duyệt */
    PENDING("Chờ duyệt"),

    /** Đang hiển thị */
    ACTIVE("Đang hiển thị"),

    /** Bị từ chối */
    REJECTED("Bị từ chối"),

    /** Đã ẩn */
    HIDDEN("Đã ẩn"),

    /** Hết hạn */
    EXPIRED("Hết hạn"),

    /** Đã đóng */
    CLOSED("Đã đóng"),

    /** Bị khóa */
    LOCKED("Bị khóa"),

    /** Cần kiểm tra */
    NEED_REVIEW("Cần kiểm tra"),

    /** Đã xóa */
    DELETED("Đã xóa");

    private final String label;

    ListingStatus(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
