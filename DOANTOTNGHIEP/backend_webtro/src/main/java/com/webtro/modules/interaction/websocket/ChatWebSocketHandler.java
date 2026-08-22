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

import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final String TYPE_SUBSCRIBE = "SUBSCRIBE";

    private final ObjectMapper objectMapper;
    private final ConversationService conversationService;
    private final ChatWebSocketRegistry registry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (currentUserId(session) == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Missing user"));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode root = objectMapper.readTree(message.getPayload());
        String type = root.path("type").asText("");
        if (!TYPE_SUBSCRIBE.equals(type)) {
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

        conversationService.assertConversationMember(conversationId, userId);
        registry.subscribe(session, conversationId);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                "type", "SUBSCRIBED",
                "conversationId", conversationId
        ))));
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

    private void sendError(WebSocketSession session, String code) throws IOException {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                    "type", "ERROR",
                    "code", code
            ))));
        }
    }
}
