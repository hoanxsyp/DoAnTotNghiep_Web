package com.webtro.modules.ai.dto.response;

import com.webtro.common.enums.ChatbotIntent;
import com.webtro.common.enums.ChatbotSender;
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
 * Một tin nhắn trong lịch sử hội thoại (docs/03 mục 7.3.3). {@code role} ∈ {USER, BOT}.
 * Theo ràng buộc {@code ck_chatbot_messages_bot_intent}, {@code intent} chỉ có ở tin của USER.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ChatbotMessageHistoryResponse", description = "Tin nhắn trong hội thoại chatbot")
public class ChatbotMessageHistoryResponse {

    private Long id;
    private ChatbotSender role;
    private String content;
    private ChatbotIntent intent;
    private BigDecimal intentConfidence;
    private Map<String, Object> extractedSlots;
    private List<Long> listingIds;
    private Integer totalResults;
    private Boolean isFallback;
    private Instant createdAt;
}
