package com.webtro.common.event;

/**
 * Tin đăng được duyệt (canonical luật 7). Module notification lắng nghe để báo cho chủ trọ và cho
 * người theo dõi chủ trọ đó {@code [§5.6]}.
 *
 * @param listingId id tin
 * @param ownerId   id chủ trọ
 */
public record ListingApprovedEvent(Long listingId, Long ownerId) {
}
