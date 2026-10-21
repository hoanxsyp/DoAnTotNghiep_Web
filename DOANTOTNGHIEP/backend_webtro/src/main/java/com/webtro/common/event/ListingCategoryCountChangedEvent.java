package com.webtro.common.event;

/**
 * Fired when a listing enters/leaves the public listing set, or moves between
 * categories while still public.
 */
public record ListingCategoryCountChangedEvent(
        Long oldCategoryId,
        Long oldWardId,
        boolean oldPubliclyVisible,
        Long newCategoryId,
        Long newWardId,
        boolean newPubliclyVisible
) {
}
