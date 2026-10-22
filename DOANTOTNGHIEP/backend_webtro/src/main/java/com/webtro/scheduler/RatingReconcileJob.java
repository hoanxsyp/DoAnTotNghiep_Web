package com.webtro.scheduler;

import com.webtro.config.properties.AppProperties;
import com.webtro.modules.interaction.service.RatingReconcileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * <b>RatingReconcileJob</b> — chạy 02:30 giờ Việt Nam mỗi đêm. Tính lại điểm phòng, điểm tin
 * (bản sao) và điểm chủ trọ từ đánh giá; báo lỗi nếu còn tin lệch điểm với phòng
 * (docs/DANH_GIA_THEO_PHONG_DOI_CHIEU.md mục 10.4).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RatingReconcileJob {

    private final RatingReconcileService reconcileService;
    private final AppProperties appProperties;

    @Scheduled(cron = "0 30 19 * * *", zone = "UTC")
    public void run() {
        if (!appProperties.getScheduler().isEnabled()) {
            return;
        }
        RatingReconcileService.Report report = reconcileService.reconcileAll();
        log.info("RatingReconcileJob: {} phòng, {} lỗi, lệch trước {} / sau {}",
                report.units(), report.errors(), report.mismatchBefore(), report.mismatchAfter());
    }
}
