package com.webtro.modules.rental.service;

import com.webtro.common.PageResponse;
import com.webtro.common.enums.RentalRequestStatus;
import com.webtro.modules.rental.dto.request.CreateRentalRequest;
import com.webtro.modules.rental.dto.request.RentalDecisionRequest;
import com.webtro.modules.rental.dto.response.RentalRequestResponse;
import org.springframework.data.domain.Pageable;

public interface RentalRequestService {
    RentalRequestResponse create(CreateRentalRequest request, Long tenantId);

    PageResponse<RentalRequestResponse> listMine(Long tenantId, RentalRequestStatus status, Pageable pageable);

    RentalRequestResponse cancel(Long requestId, Long tenantId);

    PageResponse<RentalRequestResponse> listForLandlord(Long ownerId, RentalRequestStatus status, Pageable pageable);

    RentalRequestResponse approve(Long requestId, Long ownerId, RentalDecisionRequest request);

    RentalRequestResponse reject(Long requestId, Long ownerId, RentalDecisionRequest request);

    RentalRequestResponse confirmRented(Long requestId, Long ownerId);

    RentalRequestResponse confirmCheckedOut(Long requestId, Long ownerId);
}
