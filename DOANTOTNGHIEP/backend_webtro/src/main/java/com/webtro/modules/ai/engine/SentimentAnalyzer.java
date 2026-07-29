package com.webtro.modules.ai.engine;

import com.webtro.common.enums.SentimentAction;
import com.webtro.common.enums.SentimentLabel;

import java.math.BigDecimal;
import java.util.List;

/**
 * Bộ phân tích cảm xúc bình luận tiếng Việt (canonical mục 10.1, §9.1). Đặt sau interface để thay
 * được implementation (rule-based → ML thật sau này) mà không đụng tầng service.
 *
 * <p>Hiện thực chốt: {@link VietnameseLexiconSentimentAnalyzer} — từ điển có trọng số + phủ định +
 * từ tăng cường + emoji + n-gram (canonical mục 13.2: rule-based, không cần ML thật).
 */
public interface SentimentAnalyzer {

    /** Phiên bản bộ phân tích, đối chiếu {@code sentiment_results.analyzer_version}. */
    String version();

    /**
     * Phân tích một đoạn văn bản.
     *
     * @param text        nội dung thô (đã hoặc chưa sanitize đều xử lý được)
     * @param minLength   ngưỡng {@code ai.sentiment.min_length}: ngắn hơn → NEUTRAL, không tính điểm
     * @param lowConfidence ngưỡng {@code ai.sentiment.low_confidence_threshold} (0.5): dưới ngưỡng
     *                      thì không kích hoạt hành động nặng (action tối đa {@code WATCH})
     * @param newAccount  tài khoản mới (&lt; {@code ai.sentiment.new_account_days}) → trọng số thấp
     * @param newAccountWeight trọng số cho tài khoản mới ({@code ai.sentiment.new_account_weight})
     * @return kết quả phân tích đầy đủ (không bao giờ {@code null})
     */
    SentimentOutcome analyze(String text, int minLength, BigDecimal lowConfidence,
                             boolean newAccount, BigDecimal newAccountWeight);

    /** Một token/cụm khớp từ điển kèm loại và trọng số (phục vụ giải thích). */
    record MatchedTerm(String token, String type, BigDecimal weight, int position) {
    }

    /**
     * Kết quả phân tích một bình luận.
     *
     * @param label            nhãn cảm xúc
     * @param score            điểm chuẩn hóa [-1, 1]
     * @param confidence       độ tin cậy [0, 1]
     * @param weight           trọng số bình luận trong công thức uy tín [0, 1]
     * @param suggestedAction  hành động đề xuất (NONE/WATCH/NEED_REVIEW)
     * @param riskComment      có dấu hiệu rủi ro (lừa đảo/tố cáo)
     * @param negationApplied  có áp dụng xử lý phủ định không
     * @param skipReason       lý do bỏ tính điểm ({@code null} nếu tính bình thường; ví dụ "TOO_SHORT")
     * @param positiveTerms    các từ/cụm tích cực khớp
     * @param negativeTerms    các từ/cụm tiêu cực khớp
     * @param rawScore         điểm thô trước chuẩn hóa (giải thích)
     * @param reason           câu giải thích tiếng Việt ngắn gọn
     */
    record SentimentOutcome(
            SentimentLabel label,
            BigDecimal score,
            BigDecimal confidence,
            BigDecimal weight,
            SentimentAction suggestedAction,
            boolean riskComment,
            boolean negationApplied,
            String skipReason,
            List<MatchedTerm> positiveTerms,
            List<MatchedTerm> negativeTerms,
            BigDecimal rawScore,
            String reason) {

        /** Có được tính vào điểm uy tín không (trọng số &gt; 0 và không bị bỏ qua). */
        public boolean countsTowardTrust() {
            return skipReason == null && weight != null && weight.signum() > 0;
        }
    }
}
