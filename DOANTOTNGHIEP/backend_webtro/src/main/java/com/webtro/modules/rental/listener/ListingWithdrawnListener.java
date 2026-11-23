package com.webtro.modules.rental.listener;

import com.webtro.common.enums.NotificationType;
import com.webtro.common.enums.RentalRequestStatus;
import com.webtro.common.event.ListingWithdrawnEvent;
import com.webtro.modules.notification.service.NotificationService;
import com.webtro.modules.rental.entity.RentalRequest;
import com.webtro.modules.rental.repository.RentalRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Tin bị đóng hoặc xoá thì kết thúc các yêu cầu thuê còn chờ ({@code PENDING}/{@code APPROVED})
 * của tin và báo cho người thuê. Trước đây các yêu cầu này bị treo mãi (lỗ hổng 10 trong
 * docs/DANH_GIA_THEO_PHONG_DOI_CHIEU.md).
 *
 * <p>Chạy đồng bộ trong transaction đóng/xoá tin. Yêu cầu {@code RENTED} không bị đụng tới vì máy
 * trạng thái không cho đóng hay xoá tin đang có người thuê.
 */
@Component
@RequiredArgsConstructor
public class ListingWithdrawnListener {

    private static final List<RentalRequestStatus> WAITING_STATUSES =
            List.of(RentalRequestStatus.PENDING, RentalRequestStatus.APPROVED);

    private final RentalRequestRepository rentalRequestRepository;
    private final NotificationService notificationService;

    @EventListener
    public void onListingWithdrawn(ListingWithdrawnEvent event) {
        List<RentalRequest> waiting = rentalRequestRepository
                .findByListingIdAndStatusInAndDeletedAtIsNull(event.listingId(), WAITING_STATUSES);
        if (waiting.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (RentalRequest r : waiting) {
            r.setStatus(RentalRequestStatus.REJECTED);
            r.setRejectedAt(now);
            r.setLandlordNote(event.reason());
            notificationService.notifyUser(r.getTenantId(), NotificationType.RENTAL_REQUEST_REJECTED,
                    "Yêu cầu thuê phòng đã kết thúc",
                    "Yêu cầu của bạn cho phòng \"" + event.title() + "\" đã kết thúc: " + event.reason() + ".",
                    "/tai-khoan/yeu-cau-thue", null);
        }
        rentalRequestRepository.saveAll(waiting);
    }
}
