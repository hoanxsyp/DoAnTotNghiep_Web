package com.webtro.scheduler;

import com.webtro.common.enums.ListingStatus;
import com.webtro.config.properties.AppProperties;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.listing.repository.ListingRepository;
import com.webtro.modules.listing.service.TrustScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Job 3 (canonical mục 11) — <b>TrustScoreRecalcJob</b>, chạy 02:00 UTC hằng ngày.
 *
 * <p>Tính lại điểm uy tín cho tin đang hiển thị ({@code ACTIVE}/{@code NEED_REVIEW}) và cho chủ trọ
 * của những tin đó (canonical §5.7, §5.8, mục 10.5). Điểm chủ trọ = AVG(điểm tin còn hiệu lực) nên
 * phải tính điểm tin trước, điểm chủ trọ sau.
 *
 * <p>Mỗi tin/chủ trọ được {@link TrustScoreService} xử lý trong transaction riêng
 * ({@code @Transactional} ở service) → một phần tử lỗi không chặn phần còn lại. Idempotent (chỉ đọc
 * số liệu hiện có rồi ghi đè điểm), lịch UTC, kết thúc log INFO.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrustScoreRecalcJob {

    private static final List<ListingStatus> DISPLAYED =
            List.of(ListingStatus.ACTIVE, ListingStatus.NEED_REVIEW);

    private static final int MAX_LISTINGS_PER_RUN = 20000;

    private final ListingRepository listingRepository;
    private final TrustScoreService trustScoreService;
    private final AppProperties appProperties;

    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    public void run() {
        if (!appProperties.getScheduler().isEnabled()) {
            return;
        }

        // ----- 1) Tính lại điểm từng tin đang hiển thị -----
        List<Long> listingIds = listingRepository.findIdsByStatusIn(
                DISPLAYED, PageRequest.of(0, MAX_LISTINGS_PER_RUN));
        int listingsOk = 0;
        int listingsErr = 0;
        for (Long id : listingIds) {
            try {
                trustScoreService.recalculateAndSaveListing(id);
                listingsOk++;
            } catch (RuntimeException e) {
                listingsErr++;
                log.error("TrustScoreRecalcJob: lỗi tính điểm tin id={}: {}", id, e.getMessage(), e);
            }
        }

        // ----- 2) Tính lại điểm từng chủ trọ (sau khi điểm tin đã cập nhật) -----
        List<Long> ownerIds = listingRepository.findDistinctOwnerIdsByStatusIn(DISPLAYED);
        int landlordsOk = 0;
        int landlordsSkipped = 0;
        int landlordsErr = 0;
        for (Long ownerId : ownerIds) {
            try {
                trustScoreService.recalculateLandlord(ownerId);
                landlordsOk++;
            } catch (ResourceNotFoundException e) {
                // Chủ tin chưa có hồ sơ chủ trọ (landlord_profiles) — bỏ qua, không phải lỗi hệ thống.
                landlordsSkipped++;
                log.debug("TrustScoreRecalcJob: bỏ qua chủ trọ id={} (chưa có hồ sơ): {}",
                        ownerId, e.getMessage());
            } catch (RuntimeException e) {
                landlordsErr++;
                log.error("TrustScoreRecalcJob: lỗi tính điểm chủ trọ id={}: {}", ownerId, e.getMessage(), e);
            }
        }

        log.info("TrustScoreRecalcJob hoàn tất: tin[tổng={}, ok={}, lỗi={}], "
                        + "chủ trọ[tổng={}, ok={}, bỏ qua={}, lỗi={}]",
                listingIds.size(), listingsOk, listingsErr,
                ownerIds.size(), landlordsOk, landlordsSkipped, landlordsErr);
    }
}
