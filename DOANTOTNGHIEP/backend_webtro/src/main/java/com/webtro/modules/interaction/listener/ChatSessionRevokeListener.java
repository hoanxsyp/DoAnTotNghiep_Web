package com.webtro.modules.interaction.listener;

import com.webtro.common.event.UserSessionsRevokedEvent;
import com.webtro.modules.interaction.websocket.ChatWebSocketRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;

/**
 * Đóng kết nối WebSocket chat của người dùng khi phiên đăng nhập của họ bị thu hồi (BUG-13).
 * Chỉ chạy sau khi transaction khóa tài khoản commit, để không đóng nhầm khi thao tác bị rollback.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatSessionRevokeListener {

    private final ChatWebSocketRegistry registry;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSessionsRevoked(UserSessionsRevokedEvent event) {
        int closed = registry.closeUserSessions(event.userId(),
                CloseStatus.POLICY_VIOLATION.withReason("Session revoked"));
        if (closed > 0) {
            log.info("Da dong {} ket noi WebSocket chat cua user={} (ly do {})", closed, event.userId(), event.reason());
        }
    }
}
