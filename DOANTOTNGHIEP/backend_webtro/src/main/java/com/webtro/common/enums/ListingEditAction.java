package com.webtro.common.enums;

/**
 * Loại thay đổi khi sửa tin [§3.4]. UPDATE_SENSITIVE làm tin quay về PENDING.
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum ListingEditAction {

    /** Tạo mới */
    CREATE("Tạo mới"),

    /** Sửa nhỏ */
    UPDATE_MINOR("Sửa nhỏ"),

    /** Sửa nhạy cảm */
    UPDATE_SENSITIVE("Sửa nhạy cảm"),

    /** Đổi trạng thái */
    STATUS_CHANGE("Đổi trạng thái");

    private final String label;

    ListingEditAction(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
