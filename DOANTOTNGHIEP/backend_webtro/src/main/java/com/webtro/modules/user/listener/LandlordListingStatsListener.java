package com.webtro.modules.user.listener;

import com.webtro.common.event.ListingOwnerStatsChangedEvent;
import com.webtro.modules.user.repository.LandlordProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LandlordListingStatsListener {

    private final LandlordProfileRepository landlordProfileRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void onListingOwnerStatsChanged(ListingOwnerStatsChangedEvent event) {
        Map<Long, Delta> deltas = new HashMap<>();
        if (event.oldOwnerId() != null) {
            deltas.computeIfAbsent(event.oldOwnerId(), ignored -> new Delta())
                    .add(event.oldCounted() ? -1 : 0, event.oldActive() ? -1 : 0);
        }
        if (event.newOwnerId() != null) {
            deltas.computeIfAbsent(event.newOwnerId(), ignored -> new Delta())
                    .add(event.newCounted() ? 1 : 0, event.newActive() ? 1 : 0);
        }
        deltas.forEach((ownerId, delta) -> {
            if (delta.total != 0 || delta.active != 0) {
                landlordProfileRepository.incrementListingStats(ownerId, delta.total, delta.active);
            }
        });
    }

    private static final class Delta {
        private int total;
        private int active;

        private void add(int totalDelta, int activeDelta) {
            total += totalDelta;
            active += activeDelta;
        }
    }
}
