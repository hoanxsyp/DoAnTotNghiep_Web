package com.webtro.modules.interaction.spi;

/** Quyền đánh giá và xem tin đã thuê do module rental cung cấp. */
public interface RentalEligibilityGateway {

    boolean hasRentalHistory(Long listingId, Long tenantId);
}
