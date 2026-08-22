package com.webtro.modules.listing.service;

import com.webtro.common.event.ListingCategoryCountChangedEvent;
import com.webtro.modules.listing.entity.Listing;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Publishes category-count deltas without letting catalog query listing data.
 */
@Component
@RequiredArgsConstructor
public class ListingCategoryCountPublisher {

    private final ListingVisibilityService visibilityService;
    private final ApplicationEventPublisher eventPublisher;

    public Snapshot snapshot(Listing listing) {
        return new Snapshot(
                listing == null ? null : listing.getCategoryId(),
                visibilityService.publiclyVisible(listing));
    }

    public void publishIfChanged(Snapshot before, Listing after) {
        Long oldCategoryId = before == null ? null : before.categoryId();
        boolean oldVisible = before != null && before.publiclyVisible();
        Long newCategoryId = after == null ? null : after.getCategoryId();
        boolean newVisible = visibilityService.publiclyVisible(after);

        if (Objects.equals(oldCategoryId, newCategoryId) && oldVisible == newVisible) {
            return;
        }
        eventPublisher.publishEvent(new ListingCategoryCountChangedEvent(
                oldCategoryId, oldVisible, newCategoryId, newVisible));
    }

    public record Snapshot(Long categoryId, boolean publiclyVisible) {
    }
}
