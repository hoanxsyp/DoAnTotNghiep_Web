package com.webtro.scheduler;

import com.webtro.common.enums.ListingStatus;
import com.webtro.common.enums.NotificationType;
import com.webtro.config.properties.AppProperties;
import com.webtro.modules.listing.entity.Listing;
import com.webtro.modules.listing.repository.ListingRepository;
import com.webtro.modules.listing.service.ListingCategoryCountPublisher;
import com.webtro.modules.listing.service.ListingOwnerStatsPublisher;
import com.webtro.modules.listing.statemachine.ListingEvent;
import com.webtro.modules.listing.statemachine.ListingStateMachine;
import com.webtro.modules.notification.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/**
 * Job 1 (canonical mục 11) — <b>ListingExpiryJob</b>, chạy mỗi giờ.
 *
 * <p>Chuyển các tin {@code ACTIVE}/{@code NEED_REVIEW} đã quá {@code expired_at} sang
 * {@code EXPIRED} qua {@link ListingStateMachine} (cổng duy nhất đổi trạng thái) và báo cho chủ trọ
 * ({@link NotificationType#LISTING_EXPIRED}).
 *
 * <p>Tuân "luật viết job" (canonical mục 11): idempotent (chỉ tin còn ở trạng thái quá hạn mới bị
 * đổi; chạy lại không nhân đôi), lịch theo UTC, mỗi tin xử lý trong transaction riêng và bọc
 * try/catch để một tin lỗi không chặn phần còn lại, kết thúc log INFO kèm số liệu.
 */
@Slf4j
@Component
public class ListingExpiryJob {

    /** Trạng thái được phép hết hạn (§5.1: EXPIRE từ ACTIVE, NEED_REVIEW). */
    private static final List<ListingStatus> EXPIRABLE = List.of(ListingStatus.ACTIVE, ListingStatus.NEED_REVIEW);

    /** Số tin xử lý tối đa mỗi lần chạy; phần dư (hiếm) được lần chạy kế tiếp xử lý (idempotent). */
    private static final int MAX_PER_RUN = 2000;

    private final ListingRepository listingRepository;
    private final ListingStateMachine stateMachine;
    private final ListingCategoryCountPublisher categoryCountPublisher;
    private final ListingOwnerStatsPublisher ownerStatsPublisher;
    private final NotificationService notificationService;
    private final AppProperties appProperties;
    private final TransactionTemplate txTemplate;

    public ListingExpiryJob(ListingRepository listingRepository,
                            ListingStateMachine stateMachine,
                            ListingCategoryCountPublisher categoryCountPublisher,
                            ListingOwnerStatsPublisher ownerStatsPublisher,
                            NotificationService notificationService,
                            AppProperties appProperties,
                            PlatformTransactionManager txManager) {
        this.listingRepository = listingRepository;
        this.stateMachine = stateMachine;
        this.categoryCountPublisher = categoryCountPublisher;
        this.ownerStatsPublisher = ownerStatsPublisher;
        this.notificationService = notificationService;
        this.appProperties = appProperties;
        this.txTemplate = new TransactionTemplate(txManager);
    }

    @Scheduled(cron = "0 10 * * * *", zone = "UTC")
    public void run() {
        if (!appProperties.getScheduler().isEnabled()) {
            return;
        }
        Instant now = Instant.now();
        List<Listing> candidates = listingRepository.findExpiredCandidates(
                EXPIRABLE, now, PageRequest.of(0, MAX_PER_RUN));

        int expired = 0;
        int skipped = 0;
        int errors = 0;
        for (Listing candidate : candidates) {
            Long id = candidate.getId();
            Long ownerId = candidate.getOwnerId();
            try {
                boolean changed = Boolean.TRUE.equals(txTemplate.execute(status -> expireOne(id, now)));
                if (changed) {
                    expired++;
                    notifyOwnerSafe(ownerId, id);
                } else {
                    skipped++;
                }
            } catch (RuntimeException e) {
                errors++;
                log.error("ListingExpiryJob: lỗi khi hết hạn tin id={}: {}", id, e.getMessage(), e);
            }
        }
        log.info("ListingExpiryJob hoàn tất: ứng viên={}, đã hết hạn={}, bỏ qua={}, lỗi={}",
                candidates.size(), expired, skipped, errors);
    }

    /**
     * Xử lý một tin trong transaction: nạp lại bản mới nhất, kiểm tra vẫn còn quá hạn và đúng trạng
     * thái rồi mới chuyển sang EXPIRED. Trả về {@code true} nếu thực sự đổi trạng thái.
     */
    private boolean expireOne(Long id, Instant now) {
        Listing listing = listingRepository.findAliveById(id).orElse(null);
        if (listing == null) {
            return false;
        }
        if (!EXPIRABLE.contains(listing.getStatus())) {
            return false; // đã bị đổi bởi luồng khác — idempotent
        }
        if (listing.getExpiredAt() == null || !listing.getExpiredAt().isBefore(now)) {
            return false; // đã được gia hạn trong lúc chờ
        }
        ListingCategoryCountPublisher.Snapshot categoryCountBefore = categoryCountPublisher.snapshot(listing);
        ListingOwnerStatsPublisher.Snapshot ownerStatsBefore = ownerStatsPublisher.snapshot(listing);
        ListingStatus target = stateMachine.transition(listing.getStatus(), ListingEvent.EXPIRE);
        listing.setStatus(target);
        listingRepository.save(listing);
        categoryCountPublisher.publishIfChanged(categoryCountBefore, listing);
        ownerStatsPublisher.publishIfChanged(ownerStatsBefore, listing);
        return true;
    }

    private void notifyOwnerSafe(Long ownerId, Long listingId) {
        if (ownerId == null) {
            return;
        }
        try {
            String link = appProperties.getFrontend().getListingDetailPath() + "/" + listingId;
            notificationService.notifyUser(ownerId, NotificationType.LISTING_EXPIRED,
                    NotificationType.LISTING_EXPIRED.getLabel(),
                    "Tin đăng #" + listingId + " của bạn đã hết hạn hiển thị. Gia hạn để tiếp tục hiển thị.",
                    link, null);
        } catch (RuntimeException e) {
            log.error("ListingExpiryJob: gửi thông báo hết hạn tin id={} cho user={} lỗi: {}",
                    listingId, ownerId, e.getMessage(), e);
        }
    }
}
