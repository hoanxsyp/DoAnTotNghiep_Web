package com.webtro.modules.rental.adapter;

import com.webtro.common.enums.RentalRequestStatus;
import com.webtro.modules.interaction.spi.RentalEligibilityGateway;
import com.webtro.modules.rental.repository.RentalRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RentalEligibilityGatewayAdapter implements RentalEligibilityGateway {

    private final RentalRequestRepository rentalRequestRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean hasRentalHistory(Long listingId, Long tenantId) {
        return rentalRequestRepository.existsByListingIdAndTenantIdAndStatusInAndDeletedAtIsNull(
                listingId, tenantId, List.of(RentalRequestStatus.RENTED, RentalRequestStatus.CHECKED_OUT));
    }
}
