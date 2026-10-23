package com.webtro.common.enums;

/**
 * Nguồn gốc tọa độ ({@code latitude}/{@code longitude}) của tin đăng.
 *
 * <p>Form đăng tin chưa có ô chọn vị trí, nên tin thiếu tọa độ được gán tạm tọa độ tâm phường để
 * lọc theo bán kính vẫn chạy được. Cờ này giữ cho dữ liệu trung thực: giao diện không cắm ghim
 * bản đồ như thể đó là vị trí thật của phòng.
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum LocationSource {

    /** Chủ trọ tự cung cấp — vị trí chính xác */
    OWNER("Vị trí chính xác"),

    /** Lấy theo tâm phường — vị trí gần đúng */
    WARD("Vị trí gần đúng theo phường");

    private final String label;

    LocationSource(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
