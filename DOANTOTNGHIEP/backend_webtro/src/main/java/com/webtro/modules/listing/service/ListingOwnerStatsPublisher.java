package com.webtro.modules.listing.service;

import com.webtro.common.enums.ListingStatus;
import com.webtro.common.event.ListingOwnerStatsChangedEvent;
import com.webtro.modules.listing.entity.Listing;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class ListingOwnerStatsPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public Snapshot snapshot(Listing listing) {
        boolean counted = listing != null && listing.getDeletedAt() == null;
        return new Snapshot(
                listing == null ? null : listing.getOwnerId(),
                counted,
                counted && listing.getStatus() == ListingStatus.ACTIVE);
    }

    public void publishIfChanged(Snapshot before, Listing after) {
        Long oldOwnerId = before == null ? null : before.ownerId();
        boolean oldCounted = before != null && before.counted();
        boolean oldActive = before != null && before.active();
        Snapshot current = snapshot(after);

        if (Objects.equals(oldOwnerId, current.ownerId())
                && oldCounted == current.counted()
                && oldActive == current.active()) {
            return;
        }
        eventPublisher.publishEvent(new ListingOwnerStatsChangedEvent(
                oldOwnerId, oldCounted, oldActive,
                current.ownerId(), current.counted(), current.active()));
    }

    public record Snapshot(Long ownerId, boolean counted, boolean active) {
    }
}
