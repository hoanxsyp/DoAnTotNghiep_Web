package com.webtro.modules.listing.spi;

import java.util.Collection;
import java.util.Set;

/**
 * Cổng (SPI) mà module {@code listing}/{@code search} cần từ module {@code interaction} — canonical
 * luật 4: biết người xem đã lưu những tin nào để trả cờ {@code favoritedByMe} (chi tiết tin, kết quả
 * tìm kiếm). Module {@code interaction} cung cấp {@code @Component} hiện thực (bọc
 * {@code FavoriteRepository}).
 */
public interface FavoriteStatusGateway {

    /** Trong {@code listingIds}, những tin mà {@code userId} đang lưu (bản ghi còn hiệu lực). */
    Set<Long> favoritedListingIds(Long userId, Collection<Long> listingIds);

    default boolean isFavorited(Long userId, Long listingId) {
        return favoritedListingIds(userId, Set.of(listingId)).contains(listingId);
    }
}
