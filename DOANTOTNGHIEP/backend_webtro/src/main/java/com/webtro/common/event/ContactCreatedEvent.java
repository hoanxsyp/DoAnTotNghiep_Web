package com.webtro.common.event;

/**
 * Có người liên hệ tin (canonical luật 7). Module notification báo cho chủ trọ {@code [§5.6]}.
 *
 * @param listingId  id tin được liên hệ
 * @param ownerId    id chủ trọ (người nhận thông báo)
 * @param contacterId id người thuê đã liên hệ
 */
public record ContactCreatedEvent(Long listingId, Long ownerId, Long contacterId) {
}
