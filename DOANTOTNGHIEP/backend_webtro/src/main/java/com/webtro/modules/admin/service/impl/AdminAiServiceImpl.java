package com.webtro.modules.admin.service.impl;

import com.webtro.common.PageResponse;
import com.webtro.common.enums.AiModule;
import com.webtro.common.enums.AuditAction;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessException;
import com.webtro.modules.admin.dto.request.UpdateConfigRequest;
import com.webtro.modules.admin.dto.response.AiAlertResponse;
import com.webtro.modules.admin.dto.response.AiConfigResponse;
import com.webtro.modules.admin.dto.response.AiLogItemResponse;
import com.webtro.modules.admin.dto.response.AiLogResponse;
import com.webtro.modules.admin.dto.response.AiPriceDeviationResponse;
import com.webtro.modules.admin.dto.response.ConfigItemResponse;
import com.webtro.modules.admin.dto.response.UpdateConfigResponse;
import com.webtro.modules.admin.entity.SystemConfig;
import com.webtro.modules.admin.mapper.SystemConfigMapper;
import com.webtro.modules.admin.repository.SystemConfigRepository;
import com.webtro.modules.admin.service.AdminAiService;
import com.webtro.modules.admin.service.AuditLogService;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.ai.entity.ChatbotMessage;
import com.webtro.modules.ai.entity.PredictionHistory;
import com.webtro.modules.ai.entity.RecommendationLog;
import com.webtro.modules.ai.entity.SentimentResult;
import com.webtro.modules.ai.repository.ChatbotMessageRepository;
import com.webtro.modules.ai.repository.PredictionHistoryRepository;
import com.webtro.modules.ai.repository.RecommendationLogRepository;
import com.webtro.modules.ai.repository.SentimentResultRepository;
import com.webtro.modules.listing.entity.Listing;
import com.webtro.modules.listing.repository.ListingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Cài đặt {@link AdminAiService}.
 *
 * <p>Cấu hình AI được lưu trong {@code system_configs} dưới các khóa {@code ai.*}/{@code trust.*};
 * endpoint chỉ trình bày lại theo phân hệ. Cập nhật ủy quyền {@link SystemConfigService#updateValue}
 * (validate + evict cache), ghi audit và — khi đổi trọng số uy tín/ngưỡng sentiment — đánh dấu cần
 * tính lại điểm uy tín ({@code [§9.1]}).
 *
 * <p>Log AI đọc chỉ-đọc từ repository của module {@code ai} (canonical: chỉ đọc, không ghi).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAiServiceImpl implements AdminAiService {

    private static final Duration MAX_LOG_RANGE = Duration.ofDays(90);
    private static final String AI_PREFIX = "ai.";
    private static final String TRUST_PREFIX = "trust.";

    private final SystemConfigRepository systemConfigRepository;
    private final SystemConfigService systemConfigService;
    private final SystemConfigMapper systemConfigMapper;
    private final AuditLogService auditLogService;

    private final SentimentResultRepository sentimentResultRepository;
    private final RecommendationLogRepository recommendationLogRepository;
    private final PredictionHistoryRepository predictionHistoryRepository;
    private final ChatbotMessageRepository chatbotMessageRepository;
    private final ListingRepository listingRepository;

    // ============================ Cấu hình ============================

    @Override
    @Transactional(readOnly = true)
    public AiConfigResponse getConfig() {
        List<ConfigItemResponse> modules = new ArrayList<>();
        List<ConfigItemResponse> sentiment = new ArrayList<>();
        List<ConfigItemResponse> recommendation = new ArrayList<>();
        List<ConfigItemResponse> price = new ArrayList<>();
        List<ConfigItemResponse> chatbot = new ArrayList<>();
        List<ConfigItemResponse> trustWeights = new ArrayList<>();

        for (SystemConfig c : systemConfigRepository.findByDeletedAtIsNullOrderByGroupNameAscDisplayOrderAsc()) {
            String key = c.getConfigKey();
            ConfigItemResponse item = systemConfigMapper.toResponse(c);
            if (key.startsWith(TRUST_PREFIX)) {
                trustWeights.add(item);
            } else if (key.startsWith(AI_PREFIX)) {
                if (key.endsWith(".enabled")) {
                    modules.add(item);
                } else if (key.startsWith("ai.sentiment.")) {
                    sentiment.add(item);
                } else if (key.startsWith("ai.recommendation.")) {
                    recommendation.add(item);
                } else if (key.startsWith("ai.price.")) {
                    price.add(item);
                } else if (key.startsWith("ai.chatbot.")) {
                    chatbot.add(item);
                }
            }
        }

        return AiConfigResponse.builder()
                .modules(modules)
                .sentiment(sentiment)
                .recommendation(recommendation)
                .price(price)
                .chatbot(chatbot)
                .trustWeights(trustWeights)
                .build();
    }

    @Override
    @Transactional
    public UpdateConfigResponse updateConfig(UpdateConfigRequest request, Long actorId) {
        List<UpdateConfigResponse.UpdatedItem> updated = new ArrayList<>();
        List<Long> auditIds = new ArrayList<>();
        boolean recalcNeeded = false;

        for (UpdateConfigRequest.ConfigEntry entry : request.getConfigs()) {
            String key = entry.getKey().trim();

            if (!key.startsWith(AI_PREFIX) && !key.startsWith(TRUST_PREFIX)) {
                throw new BusinessException(ErrorCode.AI_CONFIG_KEY_UNKNOWN,
                        "Khóa cấu hình AI không hợp lệ: " + key);
            }

            SystemConfig config = systemConfigRepository.findByConfigKeyAndDeletedAtIsNull(key)
                    .orElseThrow(() -> new BusinessException(ErrorCode.AI_CONFIG_KEY_UNKNOWN,
                            "Khóa cấu hình AI không hợp lệ: " + key));

            Object oldTyped = systemConfigMapper.coerce(config.getConfigValue(), config.getValueType());
            String newRaw = stringify(entry.getValue());

            try {
                systemConfigService.updateValue(key, newRaw, actorId);
            } catch (BusinessException ex) {
                if (ex.getErrorCode() == ErrorCode.VALIDATION_FAILED) {
                    throw new BusinessException(ErrorCode.AI_CONFIG_VALUE_INVALID,
                            "Giá trị cấu hình không hợp lệ cho khóa " + key + ": " + ex.getMessage());
                }
                throw ex;
            }

            if (key.startsWith(TRUST_PREFIX) || key.startsWith("ai.sentiment.")) {
                recalcNeeded = true;
            }

            Object newTyped = systemConfigMapper.coerce(newRaw, config.getValueType());
            Long auditId = auditLogService.recordChange(AuditAction.AI_CONFIG_CHANGE, actorId,
                    "CONFIG", null, key,
                    String.valueOf(config.getConfigValue()), newRaw, request.getReason());
            auditIds.add(auditId);
            updated.add(UpdateConfigResponse.UpdatedItem.builder()
                    .key(key).oldValue(oldTyped).newValue(newTyped).build());
        }

        return UpdateConfigResponse.builder()
                .updated(updated)
                .cacheInvalidated(true)
                .recalcJobQueued(recalcNeeded)
                .note(recalcNeeded
                        ? "Thay đổi trọng số/ngưỡng — điểm uy tín sẽ được tính lại ở lần chạy job kế tiếp [§9.1]"
                        : null)
                .auditLogIds(auditIds)
                .updatedAt(Instant.now())
                .build();
    }

    // ============================ Log AI ============================

    @Override
    @Transactional(readOnly = true)
    public AiLogResponse getLogs(AiModule module, Instant from, Instant to, Pageable pageable) {
        Instant effectiveTo = to != null ? to : Instant.now();
        Instant effectiveFrom = from != null ? from : effectiveTo.minus(Duration.ofDays(7));
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new BusinessException(ErrorCode.STATISTIC_RANGE_INVALID,
                    "Ngày bắt đầu phải trước ngày kết thúc");
        }
        if (Duration.between(effectiveFrom, effectiveTo).compareTo(MAX_LOG_RANGE) > 0) {
            throw new BusinessException(ErrorCode.AUDIT_LOG_RANGE_TOO_LARGE,
                    "Khoảng thời gian tra cứu tối đa 90 ngày");
        }

        PageResponse<AiLogItemResponse> results = switch (module) {
            case SENTIMENT -> {
                Page<SentimentResult> p = sentimentResultRepository.findAll(pageable);
                yield PageResponse.from(p, this::mapSentiment);
            }
            case RECOMMENDATION -> {
                Page<RecommendationLog> p = recommendationLogRepository.findAll(pageable);
                yield PageResponse.from(p, this::mapRecommendation);
            }
            case PRICE -> {
                Page<PredictionHistory> p = predictionHistoryRepository.findAll(pageable);
                yield PageResponse.from(p, this::mapPrice);
            }
            case CHATBOT -> {
                Page<ChatbotMessage> p = chatbotMessageRepository.findAll(pageable);
                yield PageResponse.from(p, this::mapChatbot);
            }
        };

        return AiLogResponse.builder().module(module.name()).results(results).build();
    }

    private AiLogItemResponse mapSentiment(SentimentResult e) {
        return AiLogItemResponse.builder()
                .id(e.getId())
                .createdAt(e.getCreatedAt())
                .commentId(e.getCommentId())
                .listingId(e.getListingId())
                .label(e.getLabel() != null ? e.getLabel().name() : null)
                .score(e.getScore())
                .confidence(e.getConfidence())
                .action(e.getSuggestedAction() != null ? e.getSuggestedAction().name() : null)
                .isRiskComment(e.getIsRiskComment())
                .processingMs(e.getProcessingMs())
                .retryCount(e.getRetryCount())
                .errorMessage(e.getErrorMessage())
                .build();
    }

    private AiLogItemResponse mapRecommendation(RecommendationLog e) {
        return AiLogItemResponse.builder()
                .id(e.getId())
                .createdAt(e.getCreatedAt())
                .userId(e.getUserId())
                .listingId(e.getListingId())
                .source(e.getSource() != null ? e.getSource().name() : null)
                .recommendationScore(e.getScore())
                .rankPosition(e.getRankPosition())
                .batchId(e.getBatchId())
                .build();
    }

    private AiLogItemResponse mapPrice(PredictionHistory e) {
        return AiLogItemResponse.builder()
                .id(e.getId())
                .createdAt(e.getCreatedAt())
                .userId(e.getUserId())
                .listingId(e.getListingId())
                .suggestedPrice(e.getSuggestedPrice())
                .inputPrice(e.getInputPrice())
                .deviationRatio(e.getDeviationRatio())
                .priceConfidence(e.getConfidence() != null ? e.getConfidence().name() : null)
                .sampleSize(e.getSampleSize())
                .build();
    }

    private AiLogItemResponse mapChatbot(ChatbotMessage e) {
        return AiLogItemResponse.builder()
                .id(e.getId())
                .createdAt(e.getCreatedAt())
                .conversationId(e.getConversation() != null ? e.getConversation().getId() : null)
                .content(e.getContent())
                .intent(e.getIntent() != null ? e.getIntent().name() : null)
                .resultCount(e.getResultCount())
                .build();
    }

    // ============================ Cảnh báo cảm xúc ============================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AiAlertResponse> getSentimentAlerts(Pageable pageable) {
        Page<Listing> page = listingRepository.findSentimentAlerts(pageable);
        return PageResponse.from(page, this::mapAlert);
    }

    private AiAlertResponse mapAlert(Listing l) {
        int negative = l.getNegativeCommentCount() != null ? l.getNegativeCommentCount() : 0;
        int positive = l.getPositiveCommentCount() != null ? l.getPositiveCommentCount() : 0;
        int total = negative + positive;
        BigDecimal ratio = total > 0
                ? BigDecimal.valueOf(negative).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                : null;
        return AiAlertResponse.builder()
                .listingId(l.getId())
                .listingTitle(l.getTitle())
                .ownerId(l.getOwnerId())
                .status(l.getStatus() != null ? l.getStatus().name() : null)
                .sentimentLabel("NEGATIVE")
                .negativeRatio(ratio)
                .negativeCount(negative)
                .totalComments(total)
                .needReviewCount(l.getNeedReviewCount())
                .trustScore(l.getTrustScore() != null ? l.getTrustScore().intValue() : null)
                .flaggedAt(l.getLastNeedReviewAt())
                .createdAt(l.getLastNeedReviewAt())
                .build();
    }

    // ============================ Tin lệch giá ============================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AiPriceDeviationResponse> getPriceDeviations(Pageable pageable) {
        Page<Listing> page = listingRepository.findPriceDeviationFlagged(pageable);
        return PageResponse.from(page, this::mapPriceDeviation);
    }

    private AiPriceDeviationResponse mapPriceDeviation(Listing l) {
        PredictionHistory prediction = predictionHistoryRepository
                .findFirstByListingIdOrderByCreatedAtDesc(l.getId()).orElse(null);
        return AiPriceDeviationResponse.builder()
                .listingId(l.getId())
                .listingTitle(l.getTitle())
                .ownerId(l.getOwnerId())
                .status(l.getStatus() != null ? l.getStatus().name() : null)
                .price(l.getPrice())
                .suggestedPrice(prediction != null ? prediction.getSuggestedPrice() : null)
                .inputPrice(prediction != null ? prediction.getInputPrice() : null)
                .deviationRatio(prediction != null ? prediction.getDeviationRatio() : null)
                .createdAt(prediction != null ? prediction.getCreatedAt() : l.getUpdatedAt())
                .build();
    }

    private String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Boolean b) {
            return b.toString();
        }
        return String.valueOf(value);
    }
}
