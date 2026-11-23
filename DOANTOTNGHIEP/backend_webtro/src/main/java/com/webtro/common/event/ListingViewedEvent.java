package com.webtro.common.event;

/**
 * Sự kiện phát ra khi có người xem chi tiết một tin công khai (không tính chủ tin).
 *
 * <p>Module {@code listing} publish, module {@code interaction} lắng nghe để ghi
 * {@code view_histories} (lịch sử xem, thống kê người xem duy nhất, tín hiệu gợi ý) — canonical
 * luật 7, tránh để listing phụ thuộc trực tiếp vào service của interaction.
 *
 * @param listingId id tin được xem
 * @param userId    id người xem, {@code null} nếu là khách
 * @param ipAddress địa chỉ IP người xem (khử trùng lượt xem của khách)
 */
public record ListingViewedEvent(Long listingId, Long userId, String ipAddress) {
}
