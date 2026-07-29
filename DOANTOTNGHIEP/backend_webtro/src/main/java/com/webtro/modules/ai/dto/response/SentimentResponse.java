package com.webtro.modules.ai.dto.response;

import com.webtro.common.enums.SentimentAction;
import com.webtro.common.enums.SentimentLabel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Kết quả phân tích cảm xúc trả cho Admin/Moderator (docs/03 mục 7.1). Cũng dùng làm dạng chung của
 * một {@code SentimentResult} khi hiển thị.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "SentimentResponse", description = "Kết quả phân tích cảm xúc")
public class SentimentResponse {

    private Long commentId;
    private Long listingId;
    private String content;
    private SentimentLabel label;
    private BigDecimal score;
    private BigDecimal confidence;
    private SentimentAction action;
    private Boolean isRiskComment;
    private BigDecimal weight;

    @Schema(description = "Lý do bỏ tính điểm (ví dụ TOO_SHORT); null nếu tính bình thường")
    private String skipReason;
    private String skipNote;

    private Explanation explanation;
    private TrustScoreImpact trustScoreImpact;

    private Integer processingTimeMs;
    private Boolean persisted;
    private Instant analyzedAt;

    /** Chi tiết giải thích để kiểm tra từ điển. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Explanation {
        private List<MatchedToken> matchedTokens;
        private Boolean negationApplied;
        private BigDecimal rawScore;
        private BigDecimal normalizedScore;
        private String reason;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchedToken {
        private String token;
        private String type;
        private BigDecimal weight;
        private Integer position;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrustScoreImpact {
        private Integer listingTrustScoreBefore;
        private Integer listingTrustScoreAfter;
        private Boolean recalculated;
    }
}
