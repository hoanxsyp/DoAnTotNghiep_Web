package com.webtro.modules.rental.adapter;

import com.webtro.common.enums.RentalRequestStatus;
import com.webtro.modules.interaction.spi.RentalEligibilityGateway;
import com.webtro.modules.rental.entity.RentalRequest;
import com.webtro.modules.rental.repository.RentalRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RentalEligibilityGatewayAdapter implements RentalEligibilityGateway {

    private static final List<RentalRequestStatus> RENTED_STATUSES =
            List.of(RentalRequestStatus.RENTED, RentalRequestStatus.CHECKED_OUT);

    private final RentalRequestRepository rentalRequestRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean hasRentalHistory(Long listingId, Long tenantId) {
        return rentalRequestRepository.existsByListingIdAndTenantIdAndStatusInAndDeletedAtIsNull(
                listingId, tenantId, RENTED_STATUSES);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> findRentedListingInUnit(Long rentalUnitId, Long tenantId) {
        return rentalRequestRepository
                .findFirstByRentalUnitIdAndTenantIdAndStatusInAndDeletedAtIsNullOrderByRentedAtDesc(
                        rentalUnitId, tenantId, RENTED_STATUSES)
                .map(RentalRequest::getListingId);
    }
}
