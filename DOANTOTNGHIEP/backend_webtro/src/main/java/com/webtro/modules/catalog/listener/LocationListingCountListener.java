package com.webtro.modules.catalog.listener;

import com.webtro.common.event.ListingCategoryCountChangedEvent;
import com.webtro.constant.CacheName;
import com.webtro.modules.catalog.repository.DistrictRepository;
import com.webtro.modules.catalog.repository.ProvinceRepository;
import com.webtro.modules.catalog.repository.WardRepository;
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

/** Keeps denormalized location counters aligned with the public listing set. */
@Component
@RequiredArgsConstructor
public class LocationListingCountListener {

    private final WardRepository wardRepository;
    private final DistrictRepository districtRepository;
    private final ProvinceRepository provinceRepository;
    private final CacheManager cacheManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onListingCountChanged(ListingCategoryCountChangedEvent event) {
        Map<Long, Integer> deltas = new HashMap<>();
        if (event.oldPubliclyVisible() && event.oldWardId() != null) {
            deltas.merge(event.oldWardId(), -1, Integer::sum);
        }
        if (event.newPubliclyVisible() && event.newWardId() != null) {
            deltas.merge(event.newWardId(), 1, Integer::sum);
        }

        boolean changed = false;
        for (Map.Entry<Long, Integer> entry : deltas.entrySet()) {
            int delta = entry.getValue();
            if (delta == 0) {
                continue;
            }
            Long wardId = entry.getKey();
            changed |= wardRepository.incrementListingCount(wardId, delta) > 0;
            districtRepository.incrementListingCountByWardId(wardId, delta);
            provinceRepository.incrementListingCountByWardId(wardId, delta);
        }

        if (changed) {
            Cache cache = cacheManager.getCache(CacheName.ADMINISTRATIVE_UNITS);
            if (cache != null) {
                cache.clear();
            }
        }
    }
}
