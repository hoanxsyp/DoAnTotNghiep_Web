package com.webtro.scheduler;

import com.webtro.config.properties.AppProperties;
import com.webtro.modules.listing.entity.Listing;
import com.webtro.modules.listing.repository.ListingRepository;
import com.webtro.modules.payment.service.PromotionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/**
 * Job 6 (canonical mục 11) — <b>PromotionExpiryJob</b>, chạy mỗi giờ.
 *
 * <p>Hai việc:
 * <ol>
 *   <li>Gọi {@link PromotionService#expireOverdueSubscriptions()} để chuyển các lượt đẩy
 *       {@code ACTIVE} đã quá {@code end_at} sang {@code EXPIRED} và gỡ cờ đẩy trên tin tương ứng.</li>
 *   <li>Quét dọn thêm: tin còn {@code is_promoted = true} nhưng đã quá {@code promoted_until} mà
 *       (vì lý do dữ liệu) chưa được gỡ cờ ở bước 1 → gỡ {@code is_promoted}/{@code promotion_priority}.
 *       Đây là lưới an toàn giữ trạng thái đẩy tin nhất quán.</li>
 * </ol>
 *
 * <p>Idempotent (chạy lại không đổi thêm gì khi đã hết lượt/đã gỡ cờ), lịch UTC, mỗi tin gỡ cờ trong
 * transaction riêng + try/catch, kết thúc log INFO.
 */
@Slf4j
@Component
public class PromotionExpiryJob {

    private static final int MAX_PER_RUN = 5000;

    private final PromotionService promotionService;
    private final ListingRepository listingRepository;
    private final AppProperties appProperties;
    private final TransactionTemplate txTemplate;

    public PromotionExpiryJob(PromotionService promotionService,
                              ListingRepository listingRepository,
                              AppProperties appProperties,
                              PlatformTransactionManager txManager) {
        this.promotionService = promotionService;
        this.listingRepository = listingRepository;
        this.appProperties = appProperties;
        this.txTemplate = new TransactionTemplate(txManager);
    }

    @Scheduled(cron = "0 15 * * * *", zone = "UTC")
    public void run() {
        if (!appProperties.getScheduler().isEnabled()) {
            return;
        }

        int expiredSubscriptions = 0;
        try {
            expiredSubscriptions = promotionService.expireOverdueSubscriptions();
        } catch (RuntimeException e) {
            log.error("PromotionExpiryJob: lỗi khi hết hạn lượt đẩy: {}", e.getMessage(), e);
        }

        // Lưới an toàn: gỡ cờ đẩy trên các tin đã quá promoted_until nhưng còn is_promoted.
        Instant now = Instant.now();
        List<Listing> stuck = listingRepository.findExpiredPromotedListings(now, PageRequest.of(0, MAX_PER_RUN));
        int flagsCleared = 0;
        int errors = 0;
        for (Listing listing : stuck) {
            Long id = listing.getId();
            try {
                boolean cleared = Boolean.TRUE.equals(txTemplate.execute(status -> clearPromotion(id, now)));
                if (cleared) {
                    flagsCleared++;
                }
            } catch (RuntimeException e) {
                errors++;
                log.error("PromotionExpiryJob: lỗi gỡ cờ đẩy tin id={}: {}", id, e.getMessage(), e);
            }
        }

        log.info("PromotionExpiryJob hoàn tất: lượt đẩy hết hạn={}, tin gỡ cờ={}, lỗi={}",
                expiredSubscriptions, flagsCleared, errors);
    }

    private boolean clearPromotion(Long id, Instant now) {
        Listing listing = listingRepository.findAliveById(id).orElse(null);
        if (listing == null || !Boolean.TRUE.equals(listing.getIsPromoted())) {
            return false;
        }
        if (listing.getPromotedUntil() == null || !listing.getPromotedUntil().isBefore(now)) {
            return false; // được gia hạn đẩy trong lúc chờ — giữ nguyên
        }
        listing.setIsPromoted(false);
        listing.setPromotionPriority(0);
        listing.setPromotedUntil(null);
        listingRepository.save(listing);
        return true;
    }
}
