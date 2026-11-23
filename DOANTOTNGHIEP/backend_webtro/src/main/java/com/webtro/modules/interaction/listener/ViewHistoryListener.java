package com.webtro.modules.interaction.listener;

import com.webtro.common.event.ListingViewedEvent;
import com.webtro.modules.interaction.service.HistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Ghi lịch sử xem khi có người xem chi tiết tin, không để lỗi ghi lịch sử chặn việc xem tin. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ViewHistoryListener {

    private final HistoryService historyService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onListingViewed(ListingViewedEvent event) {
        try {
            historyService.recordView(event.listingId(), event.userId(), null, event.ipAddress(), null, null);
        } catch (RuntimeException e) {
            log.warn("Không ghi được lịch sử xem listing={} user={}: {}",
                    event.listingId(), event.userId(), e.getMessage());
        }
    }
}
