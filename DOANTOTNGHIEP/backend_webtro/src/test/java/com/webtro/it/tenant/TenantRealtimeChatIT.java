package com.webtro.it.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IT tin nhắn realtime qua WebSocket {@code /ws/chat} (IT-WS-xx). Chạy server THẬT trên cổng ngẫu
 * nhiên và kết nối bằng client WebSocket thật; thao tác REST (tạo hội thoại, gửi tin) vẫn đi qua
 * MockMvc trên cùng ứng dụng nên sự kiện được đẩy tới đúng các kết nối đang mở.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TenantRealtimeChatIT extends TenantItSupport {

    private static final long WAIT_MS = 3000;
    private static final long QUIET_MS = 800;

    @LocalServerPort
    private int port;

    private final List<Client> clients = new ArrayList<>();

    @AfterEach
    void closeAll() {
        for (Client c : clients) {
            try {
                c.session.close();
            } catch (Exception ignored) {
                // đã đóng
            }
        }
        clients.clear();
    }

    // ================================================================== bắt tay

    @Test
    @DisplayName("IT-WS-01 Kết nối không có token hoặc token giả → handshake bị từ chối (401)")
    void handshakeRequiresValidToken() {
        assertThrows(ExecutionException.class, () -> connectRaw("ws://localhost:" + port + "/ws/chat"));
        assertThrows(ExecutionException.class, () -> connectRaw("ws://localhost:" + port + "/ws/chat?token=abc.def.ghi"));
    }

    @Test
    @DisplayName("IT-WS-02 Token đã đăng xuất (bị blacklist) → không mở được kết nối mới")
    void handshakeRejectsLoggedOutToken() throws Exception {
        Actor tenant = newTenant();
        call(HttpMethod.POST, "/api/auth/logout", tenant, Map.of("refreshToken", tenant.refreshToken()), 204);

        assertThrows(ExecutionException.class, () -> connect(tenant));
    }

    // ================================================================== sự kiện

    @Test
    @DisplayName("IT-WS-03 Tenant mở hội thoại → chủ trọ (đang online ở trang bất kỳ) nhận CONVERSATION_UPDATED, totalUnread=1")
    void newConversationPushedToLandlord() throws Exception {
        Actor landlord = newLandlord();
        Actor tenant = newTenant();
        long listingId = createActiveListing(landlord, "BOARDING_HOUSE");
        Client lc = connect(landlord);
        Client tc = connect(tenant);

        long convId = openConversation(tenant, listingId);

        JsonNode toLandlord = lc.next("CONVERSATION_UPDATED");
        assertEquals(convId, toLandlord.at("/conversation/id").asLong());
        assertEquals(1, toLandlord.get("totalUnread").asInt());
        JsonNode toTenant = tc.next("CONVERSATION_UPDATED");
        assertEquals(0, toTenant.get("totalUnread").asInt());
    }

    @Test
    @DisplayName("IT-WS-04 SUBSCRIBE hội thoại → SUBSCRIBED; bên kia gửi tin → nhận MESSAGE_CREATED đúng nội dung, đúng người gửi")
    void subscribedReceivesMessages() throws Exception {
        Actor landlord = newLandlord();
        Actor tenant = newTenant();
        long listingId = createActiveListing(landlord, "BOARDING_HOUSE");
        long convId = openConversation(tenant, listingId);
        Client tc = connect(tenant);

        tc.send(Map.of("type", "SUBSCRIBE", "conversationId", convId));
        assertEquals(convId, tc.next("SUBSCRIBED").get("conversationId").asLong());

        call(HttpMethod.POST, "/api/conversations/" + convId + "/messages", landlord, Map.of("content", "Còn phòng em nhé"), 201);

        JsonNode msg = tc.next("MESSAGE_CREATED");
        assertEquals(convId, msg.get("conversationId").asLong());
        assertEquals("Còn phòng em nhé", msg.at("/message/content").asText());
        assertEquals(landlord.id(), msg.at("/message/senderId").asLong());
        JsonNode update = tc.next("CONVERSATION_UPDATED");
        assertEquals(1, update.get("totalUnread").asInt());
    }

    @Test
    @DisplayName("IT-WS-05 Chưa SUBSCRIBE thì không nhận MESSAGE_CREATED (chỉ nhận CONVERSATION_UPDATED); UNSUBSCRIBE thì ngừng nhận")
    void unsubscribedDoesNotReceiveMessages() throws Exception {
        Actor landlord = newLandlord();
        Actor tenant = newTenant();
        long listingId = createActiveListing(landlord, "BOARDING_HOUSE");
        long convId = openConversation(tenant, listingId);
        Client tc = connect(tenant);

        call(HttpMethod.POST, "/api/conversations/" + convId + "/messages", landlord, Map.of("content", "Tin 1"), 201);
        tc.next("CONVERSATION_UPDATED");
        assertNull(tc.poll("MESSAGE_CREATED", QUIET_MS));

        tc.send(Map.of("type", "SUBSCRIBE", "conversationId", convId));
        tc.next("SUBSCRIBED");
        tc.send(Map.of("type", "UNSUBSCRIBE", "conversationId", convId));
        Thread.sleep(200);
        call(HttpMethod.POST, "/api/conversations/" + convId + "/messages", landlord, Map.of("content", "Tin 2"), 201);
        tc.next("CONVERSATION_UPDATED");
        assertNull(tc.poll("MESSAGE_CREATED", QUIET_MS));
    }

    @Test
    @DisplayName("IT-WS-06 Người ngoài SUBSCRIBE hội thoại của người khác → ERROR CONVERSATION_FORBIDDEN, không nhận tin, kết nối vẫn giữ")
    void strangerCannotSubscribe() throws Exception {
        Actor landlord = newLandlord();
        Actor tenant = newTenant();
        Actor stranger = newTenant();
        long listingId = createActiveListing(landlord, "BOARDING_HOUSE");
        long convId = openConversation(tenant, listingId);
        Client sc = connect(stranger);

        sc.send(Map.of("type", "SUBSCRIBE", "conversationId", convId));
        assertEquals("CONVERSATION_FORBIDDEN", sc.next("ERROR").get("code").asText());

        call(HttpMethod.POST, "/api/conversations/" + convId + "/messages", landlord, Map.of("content", "Bí mật"), 201);
        assertNull(sc.poll("MESSAGE_CREATED", QUIET_MS));
        assertTrue(sc.session.isOpen());

        sc.send(Map.of("type", "SUBSCRIBE", "conversationId", 999_999_999L));
        assertEquals("CONVERSATION_FORBIDDEN", sc.next("ERROR").get("code").asText());
    }

    @Test
    @DisplayName("IT-WS-07 Gói tin sai: type lạ → ERROR UNSUPPORTED_TYPE; thiếu conversationId → ERROR INVALID_SUBSCRIBE")
    void invalidFrames() throws Exception {
        Client c = connect(newTenant());
        c.send(Map.of("type", "HELLO"));
        assertEquals("UNSUPPORTED_TYPE", c.next("ERROR").get("code").asText());
        c.send(Map.of("type", "SUBSCRIBE"));
        assertEquals("INVALID_SUBSCRIBE", c.next("ERROR").get("code").asText());
        assertTrue(c.session.isOpen());
    }

    @Test
    @DisplayName("IT-WS-08 [BUG-14] Gửi chuỗi không phải JSON → kết nối KHÔNG bị server đóng")
    @Disabled("BUG-14: objectMapper.readTree ném lỗi trong handleTextMessage → Spring đóng kết nối 1011 SERVER_ERROR. "
            + "Bỏ @Disabled sau khi fix.")
    void malformedJsonKeepsConnection() throws Exception {
        Client c = connect(newTenant());
        c.session.sendMessage(new TextMessage("not-json{"));
        Thread.sleep(QUIET_MS);
        assertTrue(c.session.isOpen(), () -> "Server đóng kết nối: " + c.closeStatus);
    }

    @Test
    @DisplayName("IT-WS-09 Đánh dấu đã đọc → CONVERSATION_UPDATED totalUnread=0 tới mọi tab của người đọc")
    void markReadSyncsAllTabs() throws Exception {
        Actor landlord = newLandlord();
        Actor tenant = newTenant();
        long listingId = createActiveListing(landlord, "BOARDING_HOUSE");
        long convId = openConversation(tenant, listingId);
        call(HttpMethod.POST, "/api/conversations/" + convId + "/messages", landlord, Map.of("content", "Chào em"), 201);
        Client tab1 = connect(tenant);
        Client tab2 = connect(tenant);

        call(HttpMethod.POST, "/api/conversations/" + convId + "/read", tenant, null, 204);

        assertEquals(0, tab1.next("CONVERSATION_UPDATED").get("totalUnread").asInt());
        assertEquals(0, tab2.next("CONVERSATION_UPDATED").get("totalUnread").asInt());
    }

    @Test
    @DisplayName("IT-WS-10 Tin bị từ chối (từ cấm/quá dài) → không phát sự kiện nào")
    void rejectedMessageNotBroadcast() throws Exception {
        Actor landlord = newLandlord();
        Actor tenant = newTenant();
        long listingId = createActiveListing(landlord, "BOARDING_HOUSE");
        long convId = openConversation(tenant, listingId);
        Client lc = connect(landlord);
        lc.send(Map.of("type", "SUBSCRIBE", "conversationId", convId));
        lc.next("SUBSCRIBED");

        call(HttpMethod.POST, "/api/conversations/" + convId + "/messages", tenant, Map.of("content", "x".repeat(2001)), 400);

        assertNull(lc.poll("MESSAGE_CREATED", QUIET_MS));
        assertNull(lc.poll("CONVERSATION_UPDATED", 10));
    }

    @Test
    @DisplayName("IT-WS-11 [BUG-13] Admin khóa tenant → kết nối WebSocket đang mở phải bị đóng/ngừng nhận tin")
    @Disabled("BUG-13: token chỉ được kiểm lúc bắt tay; khóa tài khoản (hoặc đăng xuất ở thiết bị khác) không đóng "
            + "kết nối đang mở → vẫn nhận tin nhắn riêng vô thời hạn. Bỏ @Disabled sau khi fix.")
    void lockedTenantSocketStopsReceiving() throws Exception {
        Actor landlord = newLandlord();
        Actor tenant = newTenant();
        long listingId = createActiveListing(landlord, "BOARDING_HOUSE");
        long convId = openConversation(tenant, listingId);
        Client tc = connect(tenant);
        tc.send(Map.of("type", "SUBSCRIBE", "conversationId", convId));
        tc.next("SUBSCRIBED");

        call(HttpMethod.PUT, "/api/admin/users/" + tenant.id() + "/lock", admin(),
                Map.of("reason", "Vi phạm quy định nhiều lần liên tiếp"), 200);
        call(HttpMethod.POST, "/api/conversations/" + convId + "/messages", landlord, Map.of("content", "Tin sau khi khóa"), 201);

        assertNull(tc.poll("MESSAGE_CREATED", QUIET_MS), "Tài khoản đã khóa vẫn nhận tin realtime");
    }

    // ================================================================== helper

    private long openConversation(Actor tenant, long listingId) throws Exception {
        return call(HttpMethod.POST, "/api/conversations", tenant,
                Map.of("listingId", listingId, "initialMessage", "Chào anh, phòng còn trống không ạ?"), 201)
                .at("/data/id").asLong();
    }

    private Client connect(Actor actor) throws Exception {
        return connectRaw("ws://localhost:" + port + "/ws/chat?token=" + actor.token());
    }

    private Client connectRaw(String url) throws Exception {
        Client client = new Client();
        CompletableFuture<WebSocketSession> f = new StandardWebSocketClient().execute(client, null, URI.create(url));
        client.session = f.get(5, TimeUnit.SECONDS);
        clients.add(client);
        return client;
    }

    /** Client ghi lại mọi gói tin nhận được để test đọc theo loại. */
    private final class Client extends TextWebSocketHandler {
        private final BlockingQueue<JsonNode> inbox = new LinkedBlockingQueue<>();
        private final List<JsonNode> skipped = new ArrayList<>();
        private volatile WebSocketSession session;
        private volatile CloseStatus closeStatus;

        @Override
        protected void handleTextMessage(WebSocketSession s, TextMessage message) throws Exception {
            inbox.add(om.readTree(message.getPayload()));
        }

        @Override
        public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
            closeStatus = status;
        }

        void send(Object payload) throws Exception {
            session.sendMessage(new TextMessage(om.writeValueAsString(payload)));
        }

        /** Gói tin kế tiếp có type cho trước (bỏ qua loại khác) — fail nếu quá WAIT_MS. */
        JsonNode next(String type) throws InterruptedException {
            JsonNode n = poll(type, WAIT_MS);
            assertNotNull(n, "Không nhận được sự kiện " + type + " trong " + WAIT_MS + "ms");
            return n;
        }

        JsonNode poll(String type, long timeoutMs) throws InterruptedException {
            for (int i = 0; i < skipped.size(); i++) {
                if (type.equals(skipped.get(i).path("type").asText())) {
                    return skipped.remove(i);
                }
            }
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (true) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) {
                    return null;
                }
                JsonNode n = inbox.poll(left, TimeUnit.MILLISECONDS);
                if (n == null) {
                    return null;
                }
                if (type.equals(n.path("type").asText())) {
                    return n;
                }
                skipped.add(n);
            }
        }
    }
}
