package com.webtro.modules.interaction.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webtro.modules.interaction.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

/**
 * Kết nối chat dùng chung cho cả ứng dụng (một kết nối / tab đã đăng nhập).
 * <ul>
 *   <li>Mở kết nối: ghi vào sổ theo user → nhận {@code CONVERSATION_UPDATED} ở mọi trang.</li>
 *   <li>{@code SUBSCRIBE} / {@code UNSUBSCRIBE} {conversationId}: bật/tắt nhận {@code MESSAGE_CREATED}
 *       của hội thoại đang mở.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final String TYPE_SUBSCRIBE = "SUBSCRIBE";
    private static final String TYPE_UNSUBSCRIBE = "UNSUBSCRIBE";

    private final ObjectMapper objectMapper;
    private final ConversationService conversationService;
    private final ChatWebSocketRegistry registry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = currentUserId(session);
        if (userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Missing user"));
            return;
        }
        registry.registerUser(session, userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode root = objectMapper.readTree(message.getPayload());
        String type = root.path("type").asText("");
        if (!TYPE_SUBSCRIBE.equals(type) && !TYPE_UNSUBSCRIBE.equals(type)) {
            sendError(session, "UNSUPPORTED_TYPE");
            return;
        }

        Long userId = currentUserId(session);
        Long conversationId = root.path("conversationId").canConvertToLong()
                ? root.path("conversationId").asLong()
                : null;
        if (userId == null || conversationId == null) {
            sendError(session, "INVALID_SUBSCRIBE");
            return;
        }

        if (TYPE_UNSUBSCRIBE.equals(type)) {
            registry.unsubscribe(session, conversationId);
            return;
        }

        try {
            conversationService.assertConversationMember(conversationId, userId);
        } catch (RuntimeException ex) {
            // Không phải thành viên / hội thoại không tồn tại: báo lỗi, GIỮ kết nối dùng chung.
            sendError(session, "CONVERSATION_FORBIDDEN");
            return;
        }
        registry.subscribe(session, conversationId);
        registry.send(session, Map.of(
                "type", "SUBSCRIBED",
                "conversationId", conversationId
        ));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("Chat WebSocket transport error session={}: {}", session.getId(), exception.getMessage());
        registry.remove(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.remove(session);
    }

    private Long currentUserId(WebSocketSession session) {
        Object value = session.getAttributes().get(ChatWebSocketAuthInterceptor.ATTR_USER_ID);
        return value instanceof Long userId ? userId : null;
    }

    private void sendError(WebSocketSession session, String code) {
        registry.send(session, Map.of(
                "type", "ERROR",
                "code", code
        ));
    }
}
