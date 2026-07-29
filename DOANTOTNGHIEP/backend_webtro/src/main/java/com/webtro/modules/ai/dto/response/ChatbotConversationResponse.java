package com.webtro.modules.ai.dto.response;

import com.webtro.common.enums.ChatbotIntent;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;

/**
 * Một phiên hội thoại chatbot trong danh sách (docs/03 mục 7.3.2). {@code title} sinh tự động từ
 * slot đầu tiên.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ChatbotConversationResponse", description = "Phiên hội thoại chatbot")
public class ChatbotConversationResponse {

    private Long id;
    private String title;
    private ChatbotIntent lastIntent;
    private Integer messageCount;
    private Map<String, Object> activeSlots;
    private Instant createdAt;
    private Instant lastMessageAt;
}
