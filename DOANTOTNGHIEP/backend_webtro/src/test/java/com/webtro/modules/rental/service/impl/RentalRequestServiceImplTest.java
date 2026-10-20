package com.webtro.modules.rental.service.impl;

import com.webtro.common.enums.ListingStatus;
import com.webtro.common.enums.NotificationType;
import com.webtro.common.enums.RentalRequestStatus;
import com.webtro.exception.BusinessRuleException;
import com.webtro.modules.interaction.spi.ListingGateway;
import com.webtro.modules.interaction.spi.UserGateway;
import com.webtro.modules.notification.service.NotificationService;
import com.webtro.modules.rental.dto.request.UpdateViewingDateRequest;
import com.webtro.modules.rental.entity.RentalRequest;
import com.webtro.modules.rental.repository.RentalRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RentalRequestServiceImplTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final long REQUEST_ID = 10L;
    private static final long LISTING_ID = 20L;
    private static final long TENANT_ID = 30L;
    private static final long OWNER_ID = 40L;

    @Mock
    private RentalRequestRepository rentalRequestRepository;
    @Mock
    private ListingGateway listingGateway;
    @Mock
    private UserGateway userGateway;
    @Mock
    private NotificationService notificationService;

    private RentalRequestServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RentalRequestServiceImpl(
                rentalRequestRepository, listingGateway, userGateway, notificationService);
    }

    @Test
    void updateViewingDateAllowsPendingRequest() {
        RentalRequest rental = request(RentalRequestStatus.PENDING, today().plusDays(1));
        LocalDate newDate = today().plusDays(3);
        UpdateViewingDateRequest update = new UpdateViewingDateRequest();
        update.setDesiredViewingDate(newDate);
        stubRequest(rental);
        stubBriefs();
        stubSave();

        var response = service.updateViewingDate(REQUEST_ID, TENANT_ID, update);

        assertEquals(newDate, rental.getDesiredViewingDate());
        assertEquals(newDate, response.getDesiredViewingDate());
        verify(rentalRequestRepository).save(rental);
    }

    @Test
    void updateViewingDateRejectsApprovedRequest() {
        RentalRequest rental = request(RentalRequestStatus.APPROVED, today().plusDays(3));
        UpdateViewingDateRequest update = new UpdateViewingDateRequest();
        update.setDesiredViewingDate(today().plusDays(4));
        stubRequest(rental);

        assertThrows(BusinessRuleException.class,
                () -> service.updateViewingDate(REQUEST_ID, TENANT_ID, update));
        verify(rentalRequestRepository, never()).save(any());
    }

    @Test
    void cancelAllowsApprovedRequestAtLeastTwoDaysBeforeViewing() {
        RentalRequest rental = request(RentalRequestStatus.APPROVED, today().plusDays(2));
        stubRequest(rental);
        stubBriefs();
        stubSave();

        var response = service.cancel(REQUEST_ID, TENANT_ID);

        assertEquals(RentalRequestStatus.CANCELLED, rental.getStatus());
        assertEquals(RentalRequestStatus.CANCELLED, response.getStatus());
        assertNotNull(rental.getCancelledAt());
        verify(notificationService).notifyUser(
                eq(OWNER_ID), eq(NotificationType.RENTAL_REQUEST_CANCELLED),
                anyString(), anyString(), eq("/quan-ly/yeu-cau-thue"), eq(null));
    }

    @Test
    void cancelRejectsApprovedRequestOnlyOneDayBeforeViewing() {
        RentalRequest rental = request(RentalRequestStatus.APPROVED, today().plusDays(1));
        stubRequest(rental);

        assertThrows(BusinessRuleException.class, () -> service.cancel(REQUEST_ID, TENANT_ID));

        assertEquals(RentalRequestStatus.APPROVED, rental.getStatus());
        verify(rentalRequestRepository, never()).save(any());
        verify(notificationService, never()).notifyUser(
                any(), any(), anyString(), anyString(), any(), any());
    }

    private void stubRequest(RentalRequest rental) {
        when(rentalRequestRepository.findAliveByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(rental));
    }

    private void stubBriefs() {
        when(listingGateway.getBrief(LISTING_ID)).thenReturn(new ListingGateway.ListingBrief(
                LISTING_ID, OWNER_ID, "Phòng thử nghiệm", "phong-thu-nghiem", null,
                BigDecimal.valueOf(3_000_000), BigDecimal.valueOf(25), "Hà Nội",
                ListingStatus.ACTIVE, true, BigDecimal.ZERO, 0, 0));
        when(userGateway.getBrief(TENANT_ID)).thenReturn(new UserGateway.UserBrief(
                TENANT_ID, "Người thuê", null, "0900000000", null));
    }

    private void stubSave() {
        when(rentalRequestRepository.save(any(RentalRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private RentalRequest request(RentalRequestStatus status, LocalDate viewingDate) {
        return RentalRequest.builder()
                .id(REQUEST_ID)
                .listingId(LISTING_ID)
                .tenantId(TENANT_ID)
                .desiredViewingDate(viewingDate)
                .status(status)
                .build();
    }

    private LocalDate today() {
        return LocalDate.now(BUSINESS_ZONE);
    }
}
