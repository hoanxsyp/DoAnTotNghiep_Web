package com.webtro.scheduler;

import com.webtro.common.enums.SentimentLabel;
import com.webtro.config.properties.AppProperties;
import com.webtro.constant.ConfigKey;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.ai.entity.SentimentResult;
import com.webtro.modules.ai.repository.SentimentResultRepository;
import com.webtro.modules.ai.service.SentimentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Job 4 (canonical mục 11) — <b>SentimentRetryJob</b>, chạy mỗi 10 phút.
 *
 * <p>Xử lý lại các bình luận có kết quả cảm xúc còn {@code PENDING_ANALYSIS} (do engine lỗi/timeout
 * lúc tạo bình luận — §9.1: bình luận vẫn được giữ, sentiment để job retry). Chỉ lấy bản hiện hành
 * ({@code is_latest}) và còn dưới ngưỡng thử lại {@link ConfigKey#AI_SENTIMENT_MAX_RETRY}.
 *
 * <p>Việc phân tích + lưu do {@link SentimentService#processCommentSentiment} đảm nhiệm, chạy trong
 * transaction MỚI (REQUIRES_NEW) và tự tăng {@code retry_count} khi lại lỗi → idempotent, an toàn
 * lặp. Job bọc try/catch từng bình luận, kết thúc log INFO.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SentimentRetryJob {

    private static final int MAX_PER_RUN = 500;

    private final SentimentResultRepository sentimentResultRepository;
    private final SentimentService sentimentService;
    private final SystemConfigService config;
    private final AppProperties appProperties;

    @Scheduled(cron = "0 */10 * * * *", zone = "UTC")
    public void run() {
        if (!appProperties.getScheduler().isEnabled()) {
            return;
        }
        if (!config.getBoolean(ConfigKey.AI_SENTIMENT_ENABLED)) {
            log.debug("SentimentRetryJob: sentiment đang tắt, bỏ qua.");
            return;
        }
        int maxRetry = config.getInt(ConfigKey.AI_SENTIMENT_MAX_RETRY);

        List<SentimentResult> pending = sentimentResultRepository
                .findByLabelAndIsLatestTrueAndRetryCountLessThanOrderByCreatedAtAsc(
                        SentimentLabel.PENDING_ANALYSIS, maxRetry, PageRequest.of(0, MAX_PER_RUN));

        int processed = 0;
        int errors = 0;
        for (SentimentResult sr : pending) {
            Long commentId = sr.getCommentId();
            try {
                sentimentService.processCommentSentiment(commentId, sr.getListingId());
                processed++;
            } catch (RuntimeException e) {
                errors++;
                log.error("SentimentRetryJob: lỗi phân tích lại comment id={}: {}",
                        commentId, e.getMessage(), e);
            }
        }
        log.info("SentimentRetryJob hoàn tất: maxRetry={}, ứng viên={}, đã xử lý={}, lỗi={}",
                maxRetry, pending.size(), processed, errors);
    }
}
