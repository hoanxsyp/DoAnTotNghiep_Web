package com.webtro.modules.interaction.dto.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.webtro.common.PageResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Trang bình luận kèm thống kê cảm xúc (canonical 4.7.1).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CommentPageResponse", description = "Trang bình luận + thống kê cảm xúc")
public class CommentPageResponse {

    @JsonUnwrapped
    private PageResponse<CommentResponse> page;

    private SentimentSummary sentimentSummary;

    /** Đếm bình luận gốc theo nhãn cảm xúc (canonical 4.7.1). */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "SentimentSummary", description = "Thống kê cảm xúc bình luận")
    public static class SentimentSummary {
        private long positive;
        private long neutral;
        private long negative;
        private long mixed;
        private long pendingAnalysis;
    }
}
