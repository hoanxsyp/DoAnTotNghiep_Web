package com.webtro.modules.interaction.spi;

import java.util.Optional;

/** Quyền đánh giá và xem tin đã thuê do module rental cung cấp. */
public interface RentalEligibilityGateway {

    /** Người dùng từng thuê qua đúng tin này (dùng để cho người từng thuê xem tin đã đóng). */
    boolean hasRentalHistory(Long listingId, Long tenantId);

    /**
     * Tin gần nhất mà người dùng đã thuê ({@code RENTED}/{@code CHECKED_OUT}) trong phòng này, qua
     * bất kỳ tin nào của phòng (RE-1). Rỗng nghĩa là chưa từng thuê phòng.
     */
    Optional<Long> findRentedListingInUnit(Long rentalUnitId, Long tenantId);
}
