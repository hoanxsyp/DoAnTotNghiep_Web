package com.webtro.modules.interaction.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webtro.modules.interaction.dto.response.ConversationResponse;
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

/**
 * Sổ kết nối WebSocket chat, giữ trong bộ nhớ (một instance backend). Hai chỉ mục:
 * <ul>
 *   <li>theo hội thoại — kết nối đã SUBSCRIBE hội thoại nào ({@code MESSAGE_CREATED});</li>
 *   <li>theo người dùng — mọi kết nối của một user ({@code CONVERSATION_UPDATED}).</li>
 * </ul>
 * {@link WebSocketSession#sendMessage} không an toàn khi nhiều luồng cùng gửi vào một session, nên mọi
 * lần gửi đi qua {@link #send(WebSocketSession, Object)} (khóa theo session).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketRegistry implements ChatRealtimePublisher {

    private final ObjectMapper objectMapper;
    private final Map<Long, Set<WebSocketSession>> sessionsByConversation = new ConcurrentHashMap<>();
    private final Map<String, Set<Long>> conversationsBySession = new ConcurrentHashMap<>();
    private final Map<Long, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();
    private final Map<String, Long> userBySession = new ConcurrentHashMap<>();

    /** Ghi kết nối vào sổ theo người dùng (gọi ngay khi kết nối đã xác thực). */
    public void registerUser(WebSocketSession session, Long userId) {
        sessionsByUser.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
        userBySession.put(session.getId(), userId);
    }

    public void subscribe(WebSocketSession session, Long conversationId) {
        sessionsByConversation
                .computeIfAbsent(conversationId, ignored -> ConcurrentHashMap.newKeySet())
                .add(session);
        conversationsBySession
                .computeIfAbsent(session.getId(), ignored -> ConcurrentHashMap.newKeySet())
                .add(conversationId);
    }

    public void unsubscribe(WebSocketSession session, Long conversationId) {
        Set<Long> conversationIds = conversationsBySession.get(session.getId());
        if (conversationIds != null) {
            conversationIds.remove(conversationId);
        }
        removeFromIndex(sessionsByConversation, conversationId, session);
    }

    public void remove(WebSocketSession session) {
        Set<Long> conversationIds = conversationsBySession.remove(session.getId());
        if (conversationIds != null) {
            for (Long conversationId : conversationIds) {
                removeFromIndex(sessionsByConversation, conversationId, session);
            }
        }
        Long userId = userBySession.remove(session.getId());
        if (userId != null) {
            removeFromIndex(sessionsByUser, userId, session);
        }
    }

    @Override
    public void publishMessage(Long conversationId, MessageResponse message) {
        broadcast(sessionsByConversation.get(conversationId), Map.of(
                "type", "MESSAGE_CREATED",
                "conversationId", conversationId,
                "message", message
        ));
    }

    @Override
    public void publishConversationUpdate(Long userId, ConversationResponse conversation, long totalUnread) {
        broadcast(sessionsByUser.get(userId), Map.of(
                "type", "CONVERSATION_UPDATED",
                "conversation", conversation,
                "totalUnread", totalUnread
        ));
    }

    /** Gửi một payload tới một kết nối. Lỗi ghi -> bỏ kết nối khỏi sổ. */
    public void send(WebSocketSession session, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (IOException ex) {
            log.warn("Khong serialize duoc WebSocket chat payload: {}", ex.getMessage());
            return;
        }
        sendJson(session, json);
    }

    private void broadcast(Set<WebSocketSession> sessions, Object payload) {
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (IOException ex) {
            log.warn("Khong serialize duoc WebSocket chat payload: {}", ex.getMessage());
            return;
        }
        for (WebSocketSession session : sessions) {
            sendJson(session, json);
        }
    }

    private void sendJson(WebSocketSession session, String json) {
        if (!session.isOpen()) {
            remove(session);
            return;
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException | IllegalStateException ex) {
            log.warn("Khong gui duoc WebSocket chat session={}: {}", session.getId(), ex.getMessage());
            remove(session);
        }
    }

    private static void removeFromIndex(Map<Long, Set<WebSocketSession>> index, Long key, WebSocketSession session) {
        index.computeIfPresent(key, (k, sessions) -> {
            sessions.remove(session);
            return sessions.isEmpty() ? null : sessions;
        });
    }
}
