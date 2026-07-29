package com.webtro.modules.ai.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webtro.modules.ai.dto.response.ChatbotConversationResponse;
import com.webtro.modules.ai.dto.response.ChatbotMessageHistoryResponse;
import com.webtro.modules.ai.entity.ChatbotConversation;
import com.webtro.modules.ai.entity.ChatbotMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Chuyển đổi entity chatbot ↔ dto (thủ công, Builder — canonical luật 3).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatbotMapper {

    private final ObjectMapper objectMapper;

    public ChatbotConversationResponse toConversationResponse(ChatbotConversation c, String lastPreview) {
        return ChatbotConversationResponse.builder()
                .id(c.getId())
                .title(buildTitle(c))
                .lastIntent(c.getLastIntent())
                .messageCount(c.getMessageCount())
                .activeSlots(parseMap(c.getCollectedFilters()))
                .createdAt(c.getStartedAt())
                .lastMessageAt(c.getLastMessageAt())
                .build();
    }

    public ChatbotMessageHistoryResponse toMessageHistoryResponse(ChatbotMessage m) {
        return ChatbotMessageHistoryResponse.builder()
                .id(m.getId())
                .role(m.getSender())
                .content(m.getContent())
                .intent(m.getIntent())
                .intentConfidence(m.getIntentConfidence())
                .extractedSlots(parseMap(m.getExtractedSlots()))
                .listingIds(parseLongList(m.getResultListingIds()))
                .totalResults(m.getResultCount())
                .isFallback(m.getIsFallback())
                .createdAt(m.getCreatedAt())
                .build();
    }

    private String buildTitle(ChatbotConversation c) {
        Map<String, Object> slots = parseMap(c.getCollectedFilters());
        StringBuilder sb = new StringBuilder("Tìm phòng");
        if (slots != null) {
            Object loc = slots.get("locationKeyword");
            if (loc != null) {
                sb.append(" ").append(loc);
            }
            Object priceTo = slots.get("priceTo");
            if (priceTo != null) {
                sb.append(" dưới ").append(priceTo);
            }
        }
        String title = sb.toString();
        return title.length() > 120 ? title.substring(0, 120) : title;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("Không parse được JSON slot: {}", e.getMessage());
            return Map.of();
        }
    }

    private List<Long> parseLongList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Long.class));
        } catch (Exception e) {
            log.warn("Không parse được result_listing_ids: {}", e.getMessage());
            return List.of();
        }
    }
}
