package com.webtro.modules.ai.dto.response;

import com.webtro.common.enums.ChatbotIntent;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Phản hồi của chatbot (docs/03 mục 7.3.1, §3.15, §9.3). {@code disclaimer} LUÔN kèm khi trả danh
 * sách tin (không cam kết còn phòng); {@code listings} chỉ gồm tin công khai.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ChatbotMessageResponse", description = "Phản hồi chatbot")
public class ChatbotMessageResponse {

    private Long conversationId;
    private Long messageId;
    private ChatbotIntent intent;
    private BigDecimal intentConfidence;
    private String reply;

    private Map<String, Object> extractedSlots;
    private List<String> missingSlots;
    private Integer clarifyTurn;
    private Integer maxClarifyTurns;

    private List<ChatbotListingItem> listings;
    private Integer totalResults;
    private String searchUrl;

    private List<QuickReply> quickReplies;
    private List<ExpansionSuggestion> expansionSuggestions;

    private String glossaryTerm;
    private String disclaimer;
    private Boolean flaggedForReview;
    private Instant createdAt;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatbotListingItem {
        private Long id;
        private String slug;
        private String title;
        private BigDecimal price;
        private BigDecimal area;
        private String shortAddress;
        private String thumbnailUrl;
        private Integer trustScore;
        private BigDecimal averageRating;
        private Boolean promoted;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuickReply {
        private String label;
        private String action;
        private String value;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpansionSuggestion {
        private String type;
        private String label;
        private Integer estimatedCount;
    }
}
