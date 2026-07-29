package com.webtro.scheduler;

import com.webtro.common.enums.ListingStatus;
import com.webtro.common.enums.NotificationType;
import com.webtro.config.properties.AppProperties;
import com.webtro.constant.ConfigKey;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.listing.entity.Listing;
import com.webtro.modules.listing.repository.ListingRepository;
import com.webtro.modules.notification.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Job 2 (canonical mục 11) — <b>ListingExpiryReminderJob</b>, chạy 08:00 UTC hằng ngày.
 *
 * <p>Nhắc chủ trọ trước khi tin hết hạn. Số ngày nhắc đọc từ config
 * {@link ConfigKey#LISTING_EXPIRY_REMINDER_DAYS} (mặc định {@code "3,1"}). Chống gửi trùng bằng cột
 * {@code listings.expiry_reminder_sent_at}: mỗi ngày chỉ nhắc một lần cho một tin (guard
 * {@code expiry_reminder_sent_at < đầu ngày hôm nay}), nên tin được nhắc riêng ở mốc 3 ngày và mốc
 * 1 ngày (job chạy 1 lần/ngày nên số ngày còn lại giảm dần qua từng lần chạy).
 *
 * <p>Idempotent, lịch UTC, mỗi tin xử lý trong transaction riêng + try/catch, kết thúc log INFO.
 */
@Slf4j
@Component
public class ListingExpiryReminderJob {

    private static final List<ListingStatus> REMINDABLE =
            List.of(ListingStatus.ACTIVE, ListingStatus.NEED_REVIEW);

    private static final int MAX_PER_RUN = 5000;

    private final ListingRepository listingRepository;
    private final NotificationService notificationService;
    private final SystemConfigService config;
    private final AppProperties appProperties;
    private final TransactionTemplate txTemplate;

    public ListingExpiryReminderJob(ListingRepository listingRepository,
                                    NotificationService notificationService,
                                    SystemConfigService config,
                                    AppProperties appProperties,
                                    PlatformTransactionManager txManager) {
        this.listingRepository = listingRepository;
        this.notificationService = notificationService;
        this.config = config;
        this.appProperties = appProperties;
        this.txTemplate = new TransactionTemplate(txManager);
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "UTC")
    public void run() {
        if (!appProperties.getScheduler().isEnabled()) {
            return;
        }
        List<Integer> reminderDays = config.getIntList(ConfigKey.LISTING_EXPIRY_REMINDER_DAYS);
        if (reminderDays.isEmpty()) {
            log.info("ListingExpiryReminderJob: không cấu hình ngày nhắc ({}), bỏ qua.",
                    ConfigKey.LISTING_EXPIRY_REMINDER_DAYS);
            return;
        }
        int maxDay = reminderDays.stream().max(Integer::compareTo).orElse(0);

        Instant now = Instant.now();
        Instant maxThreshold = now.plus(Duration.ofDays(maxDay));
        Instant dayStart = now.truncatedTo(ChronoUnit.DAYS);

        List<Listing> candidates = listingRepository.findExpiryReminderCandidates(
                REMINDABLE, now, maxThreshold, dayStart, PageRequest.of(0, MAX_PER_RUN));

        int reminded = 0;
        int skipped = 0;
        int errors = 0;
        for (Listing candidate : candidates) {
            Long id = candidate.getId();
            Instant expiredAt = candidate.getExpiredAt();
            // Số ngày (làm tròn lên) còn lại tới lúc hết hạn.
            long daysLeft = daysUntil(now, expiredAt);
            if (!reminderDays.contains((int) daysLeft)) {
                skipped++;
                continue; // chưa đến mốc nhắc (vd còn 2 ngày mà chỉ nhắc mốc 3 và 1)
            }
            try {
                boolean sent = Boolean.TRUE.equals(txTemplate.execute(status -> markReminded(id, dayStart, now)));
                if (sent) {
                    notifyOwnerSafe(candidate.getOwnerId(), id, (int) daysLeft);
                    reminded++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException e) {
                errors++;
                log.error("ListingExpiryReminderJob: lỗi nhắc hết hạn tin id={}: {}", id, e.getMessage(), e);
            }
        }
        log.info("ListingExpiryReminderJob hoàn tất: mốc nhắc={}, ứng viên={}, đã nhắc={}, bỏ qua={}, lỗi={}",
                reminderDays, candidates.size(), reminded, skipped, errors);
    }

    /** Số ngày còn lại (làm tròn lên) từ {@code now} đến {@code expiredAt}; &lt;=0 nếu đã tới hạn. */
    private long daysUntil(Instant now, Instant expiredAt) {
        if (expiredAt == null) {
            return -1;
        }
        long minutes = Duration.between(now, expiredAt).toMinutes();
        return (long) Math.ceil(minutes / (60.0 * 24.0));
    }

    /**
     * Đặt cột dấu {@code expiry_reminder_sent_at = now} trong transaction, nhưng chỉ khi tin còn đủ
     * điều kiện và chưa được nhắc trong hôm nay (chống gửi trùng nếu job chạy lại cùng ngày).
     * Trả về {@code true} nếu vừa đánh dấu (được phép gửi thông báo).
     */
    private boolean markReminded(Long id, Instant dayStart, Instant now) {
        Listing listing = listingRepository.findAliveById(id).orElse(null);
        if (listing == null || !REMINDABLE.contains(listing.getStatus())) {
            return false;
        }
        Instant sentAt = listing.getExpiryReminderSentAt();
        if (sentAt != null && !sentAt.isBefore(dayStart)) {
            return false; // đã nhắc hôm nay
        }
        listing.setExpiryReminderSentAt(now);
        listingRepository.save(listing);
        return true;
    }

    private void notifyOwnerSafe(Long ownerId, Long listingId, int daysLeft) {
        if (ownerId == null) {
            return;
        }
        try {
            String link = appProperties.getFrontend().getListingDetailPath() + "/" + listingId;
            notificationService.notifyUser(ownerId, NotificationType.LISTING_EXPIRING,
                    NotificationType.LISTING_EXPIRING.getLabel(),
                    "Tin đăng #" + listingId + " của bạn sẽ hết hạn sau " + daysLeft
                            + " ngày. Hãy gia hạn để tin tiếp tục hiển thị.",
                    link, null);
        } catch (RuntimeException e) {
            log.error("ListingExpiryReminderJob: gửi nhắc tin id={} cho user={} lỗi: {}",
                    listingId, ownerId, e.getMessage(), e);
        }
    }
}
