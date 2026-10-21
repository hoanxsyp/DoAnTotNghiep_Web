package com.webtro.modules.interaction.listener;

import com.webtro.common.event.SearchPerformedEvent;
import com.webtro.modules.interaction.entity.SearchHistory;
import com.webtro.modules.interaction.repository.SearchHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Persists authenticated search events without coupling search to interaction persistence. */
@Component
@RequiredArgsConstructor
public class SearchHistoryListener {

    private final SearchHistoryRepository searchHistoryRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSearchPerformed(SearchPerformedEvent event) {
        if (event.userId() == null) {
            return;
        }
        searchHistoryRepository.save(SearchHistory.builder()
                .userId(event.userId())
                .keyword(event.keyword())
                .criteria(event.criteriaJson() == null || event.criteriaJson().isBlank()
                        ? "{}" : event.criteriaJson())
                .resultCount((int) Math.min(Integer.MAX_VALUE, Math.max(0L, event.resultCount())))
                .ipAddress(event.ipAddress())
                .build());
    }
}
