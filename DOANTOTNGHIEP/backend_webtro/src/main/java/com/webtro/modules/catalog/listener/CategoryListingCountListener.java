package com.webtro.modules.catalog.listener;

import com.webtro.common.event.ListingCategoryCountChangedEvent;
import com.webtro.constant.CacheName;
import com.webtro.modules.catalog.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CategoryListingCountListener {

    private final CategoryRepository categoryRepository;
    private final CacheManager cacheManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void onListingCategoryCountChanged(ListingCategoryCountChangedEvent event) {
        Map<Long, Integer> deltas = new HashMap<>();
        if (event.oldPubliclyVisible() && event.oldCategoryId() != null) {
            deltas.merge(event.oldCategoryId(), -1, Integer::sum);
        }
        if (event.newPubliclyVisible() && event.newCategoryId() != null) {
            deltas.merge(event.newCategoryId(), 1, Integer::sum);
        }

        boolean changed = false;
        for (Map.Entry<Long, Integer> entry : deltas.entrySet()) {
            int delta = entry.getValue();
            if (delta != 0) {
                changed |= categoryRepository.incrementListingCount(entry.getKey(), delta) > 0;
            }
        }
        if (changed) {
            Cache cache = cacheManager.getCache(CacheName.CATEGORIES);
            if (cache != null) {
                cache.clear();
            }
        }
    }
}
