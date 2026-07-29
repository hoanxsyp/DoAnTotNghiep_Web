package com.webtro.modules.ai.service.impl;

import com.webtro.common.enums.NotificationType;
import com.webtro.common.enums.SentimentLabel;
import com.webtro.constant.ConfigKey;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessException;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.ai.dto.request.SentimentAnalyzeRequest;
import com.webtro.modules.ai.dto.response.SentimentResponse;
import com.webtro.modules.ai.engine.AiModuleSupport;
import com.webtro.modules.ai.engine.SentimentAnalyzer;
import com.webtro.modules.ai.engine.SentimentAnalyzer.SentimentOutcome;
import com.webtro.modules.ai.entity.SentimentResult;
import com.webtro.modules.ai.mapper.SentimentMapper;
import com.webtro.modules.ai.repository.SentimentResultRepository;
import com.webtro.modules.ai.service.SentimentService;
import com.webtro.modules.ai.spi.CommentDataGateway;
import com.webtro.modules.ai.spi.CommentDataGateway.CommentSnapshot;
import com.webtro.modules.ai.spi.ListingDataGateway;
import com.webtro.modules.ai.spi.UserDataGateway;
import com.webtro.modules.listing.service.TrustScoreService;
import com.webtro.modules.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Hiện thực nghiệp vụ phân tích cảm xúc (canonical mục 10.1, §9.1). AI KHÔNG tự khóa tài khoản —
 * chỉ đề xuất {@code NEED_REVIEW} + cảnh báo (canonical mục 10).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SentimentServiceImpl implements SentimentService {

    private final SentimentAnalyzer analyzer;
    private final SentimentResultRepository sentimentResultRepository;
    private final CommentDataGateway commentGateway;
    private final UserDataGateway userGateway;
    private final ListingDataGateway listingGateway;
    private final SystemConfigService config;
    private final TrustScoreService trustScoreService;
    private final NotificationService notificationService;
    private final SentimentMapper mapper;
    private final AiModuleSupport support;

    // ------------------------------------------------------------------
    //  Endpoint chẩn đoán (Admin/Moderator)
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public SentimentResponse analyze(SentimentAnalyzeRequest request, Long actorId) {
        support.ensureEnabled(ConfigKey.AI_SENTIMENT_ENABLED);

        boolean hasText = request.getText() != null && !request.getText().isBlank();
        boolean hasComment = request.getCommentId() != null;
        if (hasText == hasComment) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Cần cung cấp đúng một trong hai: commentId hoặc text");
        }

        int minLength = config.getInt(ConfigKey.AI_SENTIMENT_MIN_LENGTH);
        BigDecimal lowConf = config.getDecimal(ConfigKey.AI_SENTIMENT_LOW_CONFIDENCE_THRESHOLD);
        int newAccountDays = config.getInt(ConfigKey.AI_SENTIMENT_NEW_ACCOUNT_DAYS);
        BigDecimal newAccountWeight = config.getDecimal(ConfigKey.AI_SENTIMENT_NEW_ACCOUNT_WEIGHT);

        if (hasText) {
            boolean newAccount = request.getAccountAgeDays() != null
                    && request.getAccountAgeDays() < newAccountDays;
            long t0 = System.nanoTime();
            SentimentOutcome outcome = support.runWithTimeout("sentiment", ConfigKey.AI_SENTIMENT_TIMEOUT_MS,
                    () -> analyzer.analyze(request.getText(), minLength, lowConf, newAccount, newAccountWeight));
            int ms = elapsedMs(t0);
            SentimentResponse.TrustScoreImpact impact = SentimentResponse.TrustScoreImpact.builder()
                    .recalculated(false).build();
            return mapper.toResponse(outcome, null, null, request.getText(), ms, false, Instant.now(), impact);
        }

        // commentId mode.
        CommentSnapshot snapshot = commentGateway.findComment(request.getCommentId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SENTIMENT_COMMENT_NOT_FOUND));
        boolean newAccount = isNewAccount(snapshot.userId(), newAccountDays);

        long t0 = System.nanoTime();
        SentimentOutcome outcome = support.runWithTimeout("sentiment", ConfigKey.AI_SENTIMENT_TIMEOUT_MS,
                () -> analyzer.analyze(snapshot.content(), minLength, lowConf, newAccount, newAccountWeight));
        int ms = elapsedMs(t0);

        boolean persist = Boolean.TRUE.equals(request.getPersist());
        SentimentResponse.TrustScoreImpact impact;
        if (persist) {
            Integer before = trustScoreOf(snapshot.listingId());
            persistOutcome(snapshot, outcome, ms);
            evaluateThresholds(snapshot.listingId());
            Integer after = trustScoreOf(snapshot.listingId());
            impact = SentimentResponse.TrustScoreImpact.builder()
                    .listingTrustScoreBefore(before)
                    .listingTrustScoreAfter(after)
                    .recalculated(true)
                    .build();
        } else {
            impact = SentimentResponse.TrustScoreImpact.builder().recalculated(false).build();
        }

        return mapper.toResponse(outcome, snapshot.id(), snapshot.listingId(), snapshot.content(),
                ms, persist, Instant.now(), impact);
    }

    // ------------------------------------------------------------------
    //  Luồng tự động (listener sau khi bình luận được tạo/sửa)
    // ------------------------------------------------------------------

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processCommentSentiment(Long commentId, Long listingId) {
        if (!config.getBoolean(ConfigKey.AI_SENTIMENT_ENABLED)) {
            log.debug("Sentiment tắt — bỏ qua phân tích comment {}", commentId);
            return;
        }
        Optional<CommentSnapshot> maybe = commentGateway.findComment(commentId);
        if (maybe.isEmpty()) {
            log.warn("Không tìm thấy bình luận {} để phân tích cảm xúc (đã xóa?)", commentId);
            return;
        }
        CommentSnapshot snapshot = maybe.get();

        int minLength = config.getInt(ConfigKey.AI_SENTIMENT_MIN_LENGTH);
        BigDecimal lowConf = config.getDecimal(ConfigKey.AI_SENTIMENT_LOW_CONFIDENCE_THRESHOLD);
        int newAccountDays = config.getInt(ConfigKey.AI_SENTIMENT_NEW_ACCOUNT_DAYS);
        BigDecimal newAccountWeight = config.getDecimal(ConfigKey.AI_SENTIMENT_NEW_ACCOUNT_WEIGHT);
        boolean newAccount = isNewAccount(snapshot.userId(), newAccountDays);

        long t0 = System.nanoTime();
        try {
            SentimentOutcome outcome = analyzer.analyze(snapshot.content(), minLength, lowConf,
                    newAccount, newAccountWeight);
            persistOutcome(snapshot, outcome, elapsedMs(t0));
            evaluateThresholds(snapshot.listingId());
            log.info("Đã phân tích cảm xúc comment {} → {} (score={})",
                    commentId, outcome.label(), outcome.score());
        } catch (RuntimeException e) {
            // §9.1: AI lỗi/timeout → bình luận VẪN được giữ, sentiment = PENDING_ANALYSIS, job retry.
            log.error("Lỗi phân tích cảm xúc comment {} → lưu PENDING_ANALYSIS: {}", commentId, e.getMessage(), e);
            savePending(snapshot, e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    //  Lưu kết quả + kiểm ngưỡng
    // ------------------------------------------------------------------

    private void persistOutcome(CommentSnapshot snapshot, SentimentOutcome outcome, int processingMs) {
        // Ép "đúng 1 phiên bản hiện hành": hạ cờ bản cũ trước, flush, rồi chèn bản mới.
        int retryCount = 0;
        Optional<SentimentResult> current = sentimentResultRepository
                .findByCommentIdAndIsLatestTrue(snapshot.id());
        if (current.isPresent()) {
            SentimentResult old = current.get();
            retryCount = old.getRetryCount() == null ? 0 : old.getRetryCount();
            old.setIsLatest(false);
            sentimentResultRepository.saveAndFlush(old);
        }

        SentimentResult entity = mapper.toEntity(snapshot.id(), snapshot.listingId(), outcome,
                analyzer.version(), processingMs, retryCount);
        sentimentResultRepository.save(entity);

        // Ghi ngược sentiment vào bình luận (module interaction cập nhật cột sentiment_*).
        commentGateway.applySentiment(snapshot.id(), outcome.label(), outcome.score(),
                outcome.confidence(), outcome.weight(), outcome.riskComment());

        // Cập nhật bộ đếm tin + tính lại điểm uy tín.
        int positive = (int) sentimentResultRepository
                .countByListingIdAndLabelAndIsLatestTrue(snapshot.listingId(), SentimentLabel.POSITIVE);
        int negative = (int) sentimentResultRepository
                .countByListingIdAndLabelAndIsLatestTrue(snapshot.listingId(), SentimentLabel.NEGATIVE);
        listingGateway.applySentimentCounts(snapshot.listingId(), positive, negative);
        trustScoreService.recalculateAndSaveListing(snapshot.listingId());
    }

    private void savePending(CommentSnapshot snapshot, String error) {
        Optional<SentimentResult> current = sentimentResultRepository
                .findByCommentIdAndIsLatestTrue(snapshot.id());
        int retryCount = 0;
        if (current.isPresent()) {
            SentimentResult old = current.get();
            retryCount = (old.getRetryCount() == null ? 0 : old.getRetryCount()) + 1;
            old.setIsLatest(false);
            sentimentResultRepository.saveAndFlush(old);
        }
        sentimentResultRepository.save(
                mapper.toPending(snapshot.id(), snapshot.listingId(), analyzer.version(), error, retryCount));
    }

    /**
     * Ngưỡng đánh dấu (§9.1): tỷ lệ tiêu cực vượt L1 → {@code FLAG_NEED_REVIEW} + báo Moderator;
     * vượt L2 → cảnh báo mức cao {@code AI_NEGATIVE_ALERT}. AI KHÔNG tự khóa tin.
     */
    private void evaluateThresholds(Long listingId) {
        long total = sentimentResultRepository.countByListingIdAndIsLatestTrue(listingId);
        if (total <= 0) {
            return;
        }
        long negative = sentimentResultRepository
                .countByListingIdAndLabelAndIsLatestTrue(listingId, SentimentLabel.NEGATIVE);
        BigDecimal ratio = BigDecimal.valueOf(negative)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);

        int minL1 = config.getInt(ConfigKey.AI_SENTIMENT_MIN_COMMENTS_L1);
        BigDecimal ratioL1 = config.getDecimal(ConfigKey.AI_SENTIMENT_NEGATIVE_RATIO_L1);
        int minL2 = config.getInt(ConfigKey.AI_SENTIMENT_MIN_COMMENTS_L2);
        BigDecimal ratioL2 = config.getDecimal(ConfigKey.AI_SENTIMENT_NEGATIVE_RATIO_L2);

        boolean l2 = total >= minL2 && ratio.compareTo(ratioL2) >= 0;
        boolean l1 = total >= minL1 && ratio.compareTo(ratioL1) >= 0;

        if (l1) {
            boolean flagged = listingGateway.flagNeedReview(listingId,
                    "AI phát hiện tỷ lệ bình luận tiêu cực cao (" + percent(ratio) + "%)");
            String title = "Tin cần kiểm tra: tỷ lệ bình luận tiêu cực cao";
            String content = "Tin #" + listingId + " có " + negative + "/" + total
                    + " bình luận tiêu cực (" + percent(ratio) + "%)"
                    + (flagged ? " — đã tự đánh dấu NEED_REVIEW." : ".");
            notificationService.notifyModerators(NotificationType.AI_NEGATIVE_ALERT, title, content,
                    "/admin/kiem-duyet?listingId=" + listingId);
        }
        if (l2) {
            notificationService.notifyModerators(NotificationType.AI_NEGATIVE_ALERT,
                    "Cảnh báo mức cao: tin nhiều bình luận tiêu cực",
                    "Tin #" + listingId + " vượt ngưỡng L2 (" + percent(ratio) + "% tiêu cực trên "
                            + total + " bình luận). Đề xuất kiểm tra tài khoản chủ trọ.",
                    "/admin/kiem-duyet?listingId=" + listingId);
        }
    }

    // ------------------------------------------------------------------
    //  Tiện ích
    // ------------------------------------------------------------------

    private boolean isNewAccount(Long userId, int newAccountDays) {
        if (userId == null) {
            return false;
        }
        return userGateway.accountCreatedAt(userId)
                .map(created -> created.isAfter(Instant.now().minus(Duration.ofDays(newAccountDays))))
                .orElse(false);
    }

    private Integer trustScoreOf(Long listingId) {
        return listingGateway.getAttribute(listingId).map(ListingDataGateway.ListingAttr::trustScore).orElse(null);
    }

    private int elapsedMs(long startNanos) {
        return (int) ((System.nanoTime() - startNanos) / 1_000_000);
    }

    private String percent(BigDecimal ratio) {
        return ratio.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).toPlainString();
    }
}
