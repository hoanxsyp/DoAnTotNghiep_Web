package com.webtro.modules.rental.listener;

import com.webtro.common.enums.NotificationType;
import com.webtro.common.enums.RentalRequestStatus;
import com.webtro.common.event.ListingWithdrawnEvent;
import com.webtro.modules.notification.service.NotificationService;
import com.webtro.modules.rental.entity.RentalRequest;
import com.webtro.modules.rental.repository.RentalRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lỗ hổng 10: đóng/xoá tin phải kết thúc các yêu cầu thuê còn chờ của tin. */
@ExtendWith(MockitoExtension.class)
class ListingWithdrawnListenerTest {

    @Mock
    private RentalRequestRepository rentalRequestRepository;
    @Mock
    private NotificationService notificationService;
    @InjectMocks
    private ListingWithdrawnListener listener;

    @Test
    void closingListingEndsWaitingRequestsAndNotifiesTenants() {
        RentalRequest pending = RentalRequest.builder().id(1L).listingId(5L).tenantId(11L)
                .status(RentalRequestStatus.PENDING).build();
        RentalRequest approved = RentalRequest.builder().id(2L).listingId(5L).tenantId(12L)
                .status(RentalRequestStatus.APPROVED).build();
        when(rentalRequestRepository.findByListingIdAndStatusInAndDeletedAtIsNull(eq(5L), any()))
                .thenReturn(List.of(pending, approved));

        listener.onListingWithdrawn(new ListingWithdrawnEvent(5L, "Phòng 101", "Tin đăng đã được chủ trọ đóng"));

        assertEquals(RentalRequestStatus.REJECTED, pending.getStatus());
        assertEquals(RentalRequestStatus.REJECTED, approved.getStatus());
        assertNotNull(pending.getRejectedAt());
        verify(notificationService).notifyUser(eq(11L), eq(NotificationType.RENTAL_REQUEST_REJECTED),
                anyString(), anyString(), anyString(), eq(null));
        verify(rentalRequestRepository).saveAll(List.of(pending, approved));
    }

    @Test
    void nothingToDoWhenNoWaitingRequests() {
        when(rentalRequestRepository.findByListingIdAndStatusInAndDeletedAtIsNull(eq(5L), any()))
                .thenReturn(List.of());

        listener.onListingWithdrawn(new ListingWithdrawnEvent(5L, "Phòng 101", "Tin đăng đã bị xóa"));

        verify(rentalRequestRepository, never()).saveAll(any());
    }
}
