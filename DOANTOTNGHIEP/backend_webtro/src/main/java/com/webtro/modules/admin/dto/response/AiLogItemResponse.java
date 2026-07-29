package com.webtro.modules.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Một dòng log AI (canonical 4.19.1) — DTO hợp nhất cho cả 4 module (SENTIMENT/RECOMMENDATION/
 * PRICE/CHATBOT). Các field không dùng của module hiện tại bị bỏ khỏi JSON ({@code NON_NULL}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "AiLogItemResponse", description = "Một dòng log AI (hợp nhất 4 module)")
public class AiLogItemResponse {

    @Schema(description = "Id bản ghi log")
    private Long id;

    @Schema(description = "Thời điểm ghi nhận")
    private Instant createdAt;

    // ---- chung ----
    private Long userId;
    private Long listingId;

    // ---- SENTIMENT ----
    private Long commentId;
    private String label;
    private BigDecimal score;
    private BigDecimal confidence;
    private String action;
    private Boolean isRiskComment;
    private Integer processingMs;
    private Integer retryCount;
    private String errorMessage;

    // ---- RECOMMENDATION ----
    private String source;
    private BigDecimal recommendationScore;
    private Integer rankPosition;
    private String batchId;

    // ---- PRICE ----
    private BigDecimal suggestedPrice;
    private BigDecimal inputPrice;
    private BigDecimal deviationRatio;
    private String priceConfidence;
    private Integer sampleSize;

    // ---- CHATBOT ----
    private Long conversationId;
    private String content;
    private String intent;
    private Integer resultCount;
}
