package com.webtro.modules.interaction.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webtro.modules.interaction.dto.response.MessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketRegistry implements ChatRealtimePublisher {

    private final ObjectMapper objectMapper;
    private final Map<Long, Set<WebSocketSession>> sessionsByConversation = new ConcurrentHashMap<>();
    private final Map<String, Set<Long>> conversationsBySession = new ConcurrentHashMap<>();

    public void subscribe(WebSocketSession session, Long conversationId) {
        sessionsByConversation
                .computeIfAbsent(conversationId, ignored -> ConcurrentHashMap.newKeySet())
                .add(session);
        conversationsBySession
                .computeIfAbsent(session.getId(), ignored -> ConcurrentHashMap.newKeySet())
                .add(conversationId);
    }

    public void remove(WebSocketSession session) {
        Set<Long> conversationIds = conversationsBySession.remove(session.getId());
        if (conversationIds == null) {
            return;
        }
        for (Long conversationId : conversationIds) {
            Set<WebSocketSession> sessions = sessionsByConversation.get(conversationId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    sessionsByConversation.remove(conversationId);
                }
            }
        }
    }

    @Override
    public void publishMessage(Long conversationId, MessageResponse message) {
        Set<WebSocketSession> sessions = sessionsByConversation.get(conversationId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        Map<String, Object> payload = Map.of(
                "type", "MESSAGE_CREATED",
                "conversationId", conversationId,
                "message", message
        );
        send(conversationId, sessions, payload);
    }

    private void send(Long conversationId, Set<WebSocketSession> sessions, Map<String, Object> payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (IOException ex) {
            log.warn("Khong serialize duoc WebSocket chat payload conversationId={}: {}",
                    conversationId, ex.getMessage());
            return;
        }

        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                remove(session);
                continue;
            }
            try {
                session.sendMessage(new TextMessage(json));
            } catch (IOException ex) {
                log.warn("Khong gui duoc WebSocket chat session={} conversationId={}: {}",
                        session.getId(), conversationId, ex.getMessage());
                remove(session);
            }
        }
    }
}
