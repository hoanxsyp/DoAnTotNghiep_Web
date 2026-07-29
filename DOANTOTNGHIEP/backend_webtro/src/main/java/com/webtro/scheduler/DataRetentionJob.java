package com.webtro.scheduler;

import com.webtro.config.properties.AppProperties;
import com.webtro.modules.admin.repository.AuditLogRepository;
import com.webtro.modules.ai.repository.ChatbotMessageRepository;
import com.webtro.modules.interaction.repository.SearchHistoryRepository;
import com.webtro.modules.interaction.repository.ViewHistoryRepository;
import com.webtro.modules.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Job 10 (canonical mục 11, §11.5) — <b>DataRetentionJob</b>, chạy 03:30 UTC hằng ngày.
 *
 * <p>Dọn log hành vi theo tuổi để các bảng append-only không phình vô hạn (làm backup/restore khả
 * thi). <b>Chỉ xóa log hành vi</b>, không đụng dữ liệu nghiệp vụ (tin, thanh toán, báo cáo).
 *
 * <p><b>Ngưỡng tuổi dùng HẰNG SỐ trong code (kèm chú thích), không phải config:</b> canonical mục 9
 * KHÔNG định nghĩa nhóm key {@code retention.*} và {@link com.webtro.constant.ConfigKey} không có
 * hằng tương ứng, nên đọc qua {@code SystemConfigService} sẽ ném lỗi thiếu key. Giá trị lấy theo
 * hướng dẫn canonical mục 11: view/search 180 ngày, thông báo đã đọc 90 ngày, chatbot 90 ngày,
 * audit 365 ngày.
 *
 * <p>Xóa theo lô, MỖI LÔ là một transaction riêng ({@code deleteAllByIdInBatch} của repository tự
 * mở transaction khi không có transaction bao ngoài) — nên {@code run()} cố ý KHÔNG
 * {@code @Transactional} để tránh giữ một transaction quá lâu. Idempotent, lịch UTC, kết thúc log INFO.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataRetentionJob {

    /** Ngưỡng tuổi (ngày) — canonical mục 11 (không có config retention.*). */
    private static final int VIEW_SEARCH_RETENTION_DAYS = 180;
    private static final int READ_NOTIFICATION_RETENTION_DAYS = 90;
    private static final int CHATBOT_RETENTION_DAYS = 90;
    private static final int AUDIT_RETENTION_DAYS = 365;

    /** Kích thước lô và trần số lô mỗi lần chạy (phần dư để lần chạy kế tiếp dọn tiếp). */
    private static final int BATCH = 500;
    private static final int MAX_BATCHES = 2000;

    private final ViewHistoryRepository viewHistoryRepository;
    private final SearchHistoryRepository searchHistoryRepository;
    private final NotificationRepository notificationRepository;
    private final ChatbotMessageRepository chatbotMessageRepository;
    private final AuditLogRepository auditLogRepository;
    private final AppProperties appProperties;

    @Scheduled(cron = "0 30 3 * * *", zone = "UTC")
    public void run() {
        if (!appProperties.getScheduler().isEnabled()) {
            return;
        }
        Instant now = Instant.now();
        Instant viewSearchThreshold = now.minus(Duration.ofDays(VIEW_SEARCH_RETENTION_DAYS));
        Instant notificationThreshold = now.minus(Duration.ofDays(READ_NOTIFICATION_RETENTION_DAYS));
        Instant chatbotThreshold = now.minus(Duration.ofDays(CHATBOT_RETENTION_DAYS));
        Instant auditThreshold = now.minus(Duration.ofDays(AUDIT_RETENTION_DAYS));

        long views = purgeInBatches("view_histories",
                page -> viewHistoryRepository.findIdsViewedBefore(viewSearchThreshold, page),
                viewHistoryRepository::deleteAllByIdInBatch);

        long searches = purgeInBatches("search_histories",
                page -> searchHistoryRepository.findIdsCreatedBefore(viewSearchThreshold, page),
                searchHistoryRepository::deleteAllByIdInBatch);

        long notifications = purgeInBatches("notifications(read)",
                page -> notificationRepository.findReadIdsReadBefore(notificationThreshold, page),
                notificationRepository::deleteAllByIdInBatch);

        long chatbotMessages = purgeInBatches("chatbot_messages",
                page -> chatbotMessageRepository.findIdsCreatedBefore(chatbotThreshold, page),
                chatbotMessageRepository::deleteAllByIdInBatch);

        long auditLogs = purgeInBatches("audit_logs",
                page -> auditLogRepository.findIdsCreatedBefore(auditThreshold, page),
                auditLogRepository::deleteAllByIdInBatch);

        log.info("DataRetentionJob hoàn tất: view_histories={}, search_histories={}, "
                        + "notifications(đã đọc)={}, chatbot_messages={}, audit_logs={}",
                views, searches, notifications, chatbotMessages, auditLogs);
    }

    /**
     * Xóa theo lô: nạp tối đa {@link #BATCH} id cũ hơn ngưỡng rồi xóa cả lô, lặp tới khi hết hoặc
     * chạm trần {@link #MAX_BATCHES}. Mỗi lô xóa là một transaction riêng của repository.
     *
     * @return tổng số bản ghi đã xóa
     */
    private long purgeInBatches(String table, Function<PageRequest, List<Long>> finder,
                                Consumer<List<Long>> deleter) {
        long total = 0;
        int batches = 0;
        while (batches < MAX_BATCHES) {
            List<Long> ids = finder.apply(PageRequest.of(0, BATCH));
            if (ids.isEmpty()) {
                break;
            }
            try {
                deleter.accept(ids);
                total += ids.size();
            } catch (RuntimeException e) {
                log.error("DataRetentionJob: lỗi xóa lô {} ({} bản ghi): {}",
                        table, ids.size(), e.getMessage(), e);
                break; // dừng bảng này để tránh lặp vô hạn khi lô lỗi lặp lại
            }
            batches++;
            if (ids.size() < BATCH) {
                break;
            }
        }
        if (batches >= MAX_BATCHES) {
            log.warn("DataRetentionJob: bảng {} chạm trần {} lô/lần chạy, phần dư sẽ dọn ở lần sau.",
                    table, MAX_BATCHES);
        }
        return total;
    }
}
