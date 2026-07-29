package com.webtro.scheduler;

import com.webtro.config.properties.AppProperties;
import com.webtro.constant.ConfigKey;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.interaction.repository.SearchHistoryRepository;
import com.webtro.modules.interaction.repository.ViewHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Job 5 (canonical mục 11) — <b>RecommendationPrecomputeJob</b>, chạy mỗi 6 giờ.
 *
 * <p><b>Ghi chú thiết kế (quan trọng, không phải job rỗng):</b> gợi ý của hệ thống được sinh theo
 * ngữ cảnh tại thời điểm request ({@code RecommendationEngine} content-based, có cache Redis TTL
 * {@link ConfigKey#AI_RECOMMENDATION_CACHE_TTL_MINUTES} — canonical mục 10.2). Hiện <b>không có
 * API precompute</b> nào ({@code RecommendationService} chỉ có {@code recommend(...)} cần
 * {@code request}/{@code principal} runtime), nên job này KHÔNG dựng sẵn danh sách gợi ý được.
 *
 * <p>Thay vào đó job làm phần hữu ích và rẻ: đo quy mô "người dùng hoạt động" (có xem/tìm kiếm
 * trong cửa sổ {@link ConfigKey#AI_RECOMMENDATION_NOTIFY_ACTIVE_USER_DAYS}) để giám sát tải gợi ý
 * và làm mốc cho việc làm nóng cache sau này. Nếu về sau bổ sung API precompute thì gọi tại đây.
 *
 * <p>Idempotent (chỉ đọc + log), lịch UTC, kết thúc log INFO kèm số liệu.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationPrecomputeJob {

    private final ViewHistoryRepository viewHistoryRepository;
    private final SearchHistoryRepository searchHistoryRepository;
    private final SystemConfigService config;
    private final AppProperties appProperties;

    @Scheduled(cron = "0 0 */6 * * *", zone = "UTC")
    public void run() {
        if (!appProperties.getScheduler().isEnabled()) {
            return;
        }
        if (!config.getBoolean(ConfigKey.AI_RECOMMENDATION_ENABLED)) {
            log.debug("RecommendationPrecomputeJob: recommendation đang tắt, bỏ qua.");
            return;
        }

        int activeDays = config.getInt(ConfigKey.AI_RECOMMENDATION_NOTIFY_ACTIVE_USER_DAYS);
        Instant since = Instant.now().minus(Duration.ofDays(activeDays));

        long usersByView;
        long usersBySearch;
        try {
            usersByView = viewHistoryRepository.countDistinctActiveUsersSince(since);
            usersBySearch = searchHistoryRepository.countDistinctActiveUsersSince(since);
        } catch (RuntimeException e) {
            log.error("RecommendationPrecomputeJob: lỗi khi đếm người dùng hoạt động: {}", e.getMessage(), e);
            return;
        }

        // Chưa có API precompute nên chỉ ghi nhận quy mô. Không dựng dữ liệu để tránh "job giả".
        log.info("RecommendationPrecomputeJob hoàn tất: cửa sổ hoạt động={} ngày, "
                        + "người dùng có lượt xem={}, có lượt tìm kiếm={}. "
                        + "(Chưa có API precompute — gợi ý vẫn sinh theo request + cache; job chỉ giám sát tải.)",
                activeDays, usersByView, usersBySearch);
    }
}
