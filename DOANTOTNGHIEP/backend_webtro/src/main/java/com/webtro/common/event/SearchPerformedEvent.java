package com.webtro.common.event;

/**
 * Sự kiện phát ra khi một người dùng ĐÃ ĐĂNG NHẬP thực hiện tìm kiếm (SRCH-01..08, {@code [§3.7]}
 * "Tìm kiếm của người đăng nhập được lưu để phục vụ gợi ý").
 *
 * <p>Module {@code search} publish, module {@code interaction} lắng nghe để ghi
 * {@code search_histories} (bảng thuộc interaction) — giao tiếp phi đồng bộ, KHÔNG chặn response
 * tìm kiếm, đúng canonical luật 7 (tránh phụ thuộc vòng search &harr; interaction). Khách ẩn danh
 * KHÔNG phát sự kiện này.
 *
 * @param userId      id người tìm (luôn khác {@code null} — chỉ phát khi đã đăng nhập)
 * @param keyword     từ khóa đã nhập (có thể {@code null})
 * @param criteriaJson toàn bộ bộ lọc dạng JSON (để tái áp dụng + tính hồ sơ sở thích)
 * @param resultCount số kết quả khớp (phục vụ phát hiện "tìm kiếm ít kết quả")
 * @param ipAddress   địa chỉ IP người gọi (có thể {@code null})
 */
public record SearchPerformedEvent(
        Long userId,
        String keyword,
        String criteriaJson,
        long resultCount,
        String ipAddress) {
}
