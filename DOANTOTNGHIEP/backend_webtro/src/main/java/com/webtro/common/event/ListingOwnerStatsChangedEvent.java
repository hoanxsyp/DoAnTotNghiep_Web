package com.webtro.common.event;

public record ListingOwnerStatsChangedEvent(
        Long oldOwnerId,
        boolean oldCounted,
        boolean oldActive,
        Long newOwnerId,
        boolean newCounted,
        boolean newActive
) {
}
