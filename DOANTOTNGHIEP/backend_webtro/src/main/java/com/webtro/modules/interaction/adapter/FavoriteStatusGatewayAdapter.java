package com.webtro.modules.interaction.adapter;

import com.webtro.modules.interaction.repository.FavoriteRepository;
import com.webtro.modules.listing.spi.FavoriteStatusGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Adapter (canonical luật 4/7) — hiện thực SPI {@link FavoriteStatusGateway} của module
 * {@code listing} bằng {@link FavoriteRepository}. Một truy vấn IN cho cả trang kết quả.
 */
@Component
@RequiredArgsConstructor
public class FavoriteStatusGatewayAdapter implements FavoriteStatusGateway {

    private final FavoriteRepository favoriteRepository;

    @Override
    @Transactional(readOnly = true)
    public Set<Long> favoritedListingIds(Long userId, Collection<Long> listingIds) {
        if (userId == null || listingIds == null || listingIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(favoriteRepository.findFavoritedListingIds(userId, listingIds));
    }
}
