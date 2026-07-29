package com.webtro.modules.ai.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webtro.modules.ai.dto.response.SentimentResponse;
import com.webtro.modules.ai.engine.SentimentAnalyzer.MatchedTerm;
import com.webtro.modules.ai.engine.SentimentAnalyzer.SentimentOutcome;
import com.webtro.modules.ai.entity.SentimentResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Chuyển đổi giữa kết quả engine ↔ {@link SentimentResult} ↔ {@link SentimentResponse} (thủ công,
 * Builder — canonical luật 3). Nơi duy nhất chuyển entity↔dto cho sentiment.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SentimentMapper {

    private final ObjectMapper objectMapper;

    /** Dựng entity mới (is_latest = true) từ kết quả phân tích thành công. */
    public SentimentResult toEntity(Long commentId, Long listingId, SentimentOutcome outcome,
                                    String analyzerVersion, int processingMs, int retryCount) {
        return SentimentResult.builder()
                .commentId(commentId)
                .listingId(listingId)
                .label(outcome.label())
                .score(outcome.score())
                .confidence(outcome.confidence())
                .isRiskComment(outcome.riskComment())
                .suggestedAction(outcome.suggestedAction())
                .weight(outcome.weight())
                .matchedPositiveTerms(toJson(outcome.positiveTerms()))
                .matchedNegativeTerms(toJson(outcome.negativeTerms()))
                .negationApplied(outcome.negationApplied())
                .analyzerVersion(analyzerVersion)
                .processingMs(processingMs)
                .retryCount(retryCount)
                .isLatest(true)
                .analyzedAt(Instant.now())
                .build();
    }

    /** Entity trạng thái PENDING_ANALYSIS khi engine lỗi/timeout (§9.1). */
    public SentimentResult toPending(Long commentId, Long listingId, String analyzerVersion,
                                     String errorMessage, int retryCount) {
        return SentimentResult.builder()
                .commentId(commentId)
                .listingId(listingId)
                .label(com.webtro.common.enums.SentimentLabel.PENDING_ANALYSIS)
                .suggestedAction(com.webtro.common.enums.SentimentAction.NONE)
                .weight(java.math.BigDecimal.ZERO)
                .negationApplied(false)
                .analyzerVersion(analyzerVersion)
                .errorMessage(truncate(errorMessage, 500))
                .retryCount(retryCount)
                .isLatest(true)
                .analyzedAt(null)
                .build();
    }

    /** Kết quả cho endpoint chẩn đoán từ engine outcome. */
    public SentimentResponse toResponse(SentimentOutcome outcome, Long commentId, Long listingId,
                                        String content, int processingMs, boolean persisted,
                                        Instant analyzedAt, SentimentResponse.TrustScoreImpact impact) {
        List<SentimentResponse.MatchedToken> tokens = new ArrayList<>();
        for (MatchedTerm t : outcome.positiveTerms()) {
            tokens.add(token(t));
        }
        for (MatchedTerm t : outcome.negativeTerms()) {
            tokens.add(token(t));
        }
        SentimentResponse.Explanation explanation = SentimentResponse.Explanation.builder()
                .matchedTokens(tokens)
                .negationApplied(outcome.negationApplied())
                .rawScore(outcome.rawScore())
                .normalizedScore(outcome.score())
                .reason(outcome.reason())
                .build();

        String skipNote = outcome.skipReason() == null ? null : outcome.reason();

        return SentimentResponse.builder()
                .commentId(commentId)
                .listingId(listingId)
                .content(content)
                .label(outcome.label())
                .score(outcome.score())
                .confidence(outcome.confidence())
                .action(outcome.suggestedAction())
                .isRiskComment(outcome.riskComment())
                .weight(outcome.weight())
                .skipReason(outcome.skipReason())
                .skipNote(skipNote)
                .explanation(explanation)
                .trustScoreImpact(impact)
                .processingTimeMs(processingMs)
                .persisted(persisted)
                .analyzedAt(analyzedAt)
                .build();
    }

    private SentimentResponse.MatchedToken token(MatchedTerm t) {
        return SentimentResponse.MatchedToken.builder()
                .token(t.token())
                .type(t.type())
                .weight(t.weight())
                .position(t.position())
                .build();
    }

    private String toJson(List<MatchedTerm> terms) {
        try {
            List<Object> simplified = new ArrayList<>();
            for (MatchedTerm t : terms) {
                simplified.add(java.util.Map.of(
                        "token", t.token(), "type", t.type(),
                        "weight", t.weight(), "position", t.position()));
            }
            return objectMapper.writeValueAsString(simplified);
        } catch (JsonProcessingException e) {
            log.warn("Không serialize được matched terms: {}", e.getMessage());
            return "[]";
        }
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
