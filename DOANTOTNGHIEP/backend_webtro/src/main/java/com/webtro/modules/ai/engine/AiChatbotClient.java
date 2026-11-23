package com.webtro.modules.ai.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webtro.config.properties.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gọi module AI chatbot ({@code chatbot-api}): PhoBERT hiểu câu → tìm trên tin đăng THẬT (qua API
 * công khai của chính backend này) → câu trả lời (mẫu hoặc Gemini có guardrail chống bịa).
 *
 * <p>Hợp đồng JSON của {@code chatbot-api} là snake_case ({@code session_id}, {@code active_filters},
 * {@code nlu_confidence}); đọc dạng cây để không phụ thuộc cấu hình Jackson của backend.
 * Mọi lỗi (sập, timeout, 5xx) quy về {@link Unavailable} — tầng service quyết định báo lỗi.
 */
@Slf4j
@Component
public class AiChatbotClient {

    /** Phòng trong câu trả lời của chatbot AI — đều là tin đăng thật của web. */
    public record AiRoom(Long id, String slug, String title, BigDecimal price, BigDecimal area,
                         String address, Double distanceM, String thumbnailUrl) {
    }

    /**
     * @param intent        intent của PhoBERT (search_room, refine_search, room_detail, compare_rooms,
     *                      book_appointment, out_of_scope; calculate_cost/policy_inquiry do module tách)
     * @param path          FAST (câu mẫu) | LLM (Gemini) | TEMPLATE (Gemini lỗi/bị guardrail chặn) | CLARIFY
     * @param activeFilters bộ lọc đang áp dụng trong ngữ cảnh hội thoại (Redis phía module)
     */
    public record AiReply(String reply, String intent, BigDecimal confidence, String path, boolean relaxed,
                          String rankedBy, boolean hallucinationDetected, Map<String, Object> activeFilters,
                          List<AiRoom> rooms) {
    }

    /** chatbot-api không phản hồi được (sập / timeout / lỗi). */
    public static class Unavailable extends RuntimeException {
        public Unavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final AppProperties.ChatbotApi props;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public AiChatbotClient(AppProperties appProperties, ObjectMapper mapper) {
        this.props = appProperties.getAi().getChatbotApi();
        this.mapper = mapper;
        this.http = HttpClient.newBuilder().connectTimeout(props.getConnectTimeout()).build();
    }

    public boolean isEnabled() {
        return props.isEnabled();
    }

    public AiReply chat(String sessionId, String message, Long userId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("session_id", sessionId);
        body.put("message", message);
        if (userId != null) {
            body.put("user_id", userId);
        }
        JsonNode r = post("/api/v1/chat", body);
        JsonNode meta = r.path("meta");
        List<AiRoom> rooms = new ArrayList<>();
        r.path("rooms").forEach(x -> rooms.add(new AiRoom(
                x.path("id").asLong(),
                text(x, "slug"),
                text(x, "title"),
                x.hasNonNull("price") ? x.get("price").decimalValue() : null,
                x.hasNonNull("area") ? x.get("area").decimalValue() : null,
                text(x, "address"),
                x.hasNonNull("distance_m") ? x.get("distance_m").asDouble() : null,
                text(x, "thumbnail"))));
        @SuppressWarnings("unchecked")
        Map<String, Object> filters = r.hasNonNull("active_filters")
                ? mapper.convertValue(r.get("active_filters"), Map.class) : Map.of();
        return new AiReply(
                text(r, "reply"),
                text(r, "intent"),
                meta.hasNonNull("nlu_confidence") ? BigDecimal.valueOf(meta.get("nlu_confidence").asDouble()) : null,
                text(meta, "path"),
                meta.path("relaxed").asBoolean(false),
                text(meta, "ranked_by"),
                meta.path("hallucination_detected").asBoolean(false),
                filters,
                rooms);
    }

    /** Xóa ngữ cảnh hội thoại phía module — lỗi chỉ ghi log (lượt chat vẫn tiếp tục). */
    public void reset(String sessionId) {
        try {
            post("/api/v1/chat/reset", Map.of("session_id", sessionId));
        } catch (Unavailable e) {
            log.warn("Không reset được ngữ cảnh chatbot AI cho session {}: {}", sessionId, e.getMessage());
        }
    }

    private JsonNode post(String path, Object body) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(props.getBaseUrl() + path))
                    .timeout(props.getRequestTimeout())
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() / 100 != 2) {
                throw new Unavailable("chatbot-api trả HTTP " + res.statusCode(), null);
            }
            return mapper.readTree(res.body());
        } catch (Unavailable e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Unavailable("Bị ngắt khi gọi chatbot-api", e);
        } catch (Exception e) {
            throw new Unavailable("Không gọi được chatbot-api: " + e.getMessage(), e);
        }
    }

    private static String text(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).asText() : null;
    }
}
