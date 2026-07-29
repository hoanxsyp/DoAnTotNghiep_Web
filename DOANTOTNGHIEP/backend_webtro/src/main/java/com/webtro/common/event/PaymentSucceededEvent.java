package com.webtro.common.event;

/**
 * Thanh toán thành công (canonical luật 7). Module payment kích hoạt gói đẩy tin và notification
 * báo cho chủ trọ {@code [§8.2][§5.6]}.
 *
 * @param paymentId id giao dịch
 * @param userId    id người thanh toán
 * @param listingId id tin được đẩy (có thể null nếu không gắn tin)
 */
public record PaymentSucceededEvent(Long paymentId, Long userId, Long listingId) {
}
