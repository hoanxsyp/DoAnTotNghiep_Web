package com.webtro.common.event;

/**
 * Fired when a listing enters/leaves the public listing set, or moves between
 * categories while still public.
 */
public record ListingCategoryCountChangedEvent(
        Long oldCategoryId,
        boolean oldPubliclyVisible,
        Long newCategoryId,
        boolean newPubliclyVisible
) {
}
