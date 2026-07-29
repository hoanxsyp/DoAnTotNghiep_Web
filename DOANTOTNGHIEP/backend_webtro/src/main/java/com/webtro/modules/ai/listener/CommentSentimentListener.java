package com.webtro.modules.ai.listener;

import com.webtro.common.event.CommentCreatedEvent;
import com.webtro.modules.ai.service.SentimentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Nghe {@link CommentCreatedEvent} để chạy phân tích cảm xúc bất đồng bộ (canonical mục 10, luật 7).
 *
 * <p><b>{@code AFTER_COMMIT}</b>: chỉ chạy sau khi transaction tạo bình luận đã commit — tránh race
 * "đọc DB trước commit". <b>{@code @Async("aiTaskExecutor")}</b>: không chặn request người dùng và
 * để lỗi AI không rollback việc tạo bình luận (§9.1). Sự kiện chỉ mang id (luật 7/8), listener tự
 * nạp lại trong transaction mới ({@code REQUIRES_NEW} bên trong service).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommentSentimentListener {

    private final SentimentService sentimentService;

    @Async("aiTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentCreated(CommentCreatedEvent event) {
        try {
            sentimentService.processCommentSentiment(event.commentId(), event.listingId());
        } catch (RuntimeException e) {
            // Không để lỗi async lan ra — đã lưu PENDING_ANALYSIS bên trong service; job retry sẽ xử lý lại.
            log.error("Phân tích cảm xúc thất bại cho comment {}: {}",
                    event.commentId(), e.getMessage(), e);
        }
    }
}
