package com.webtro.it.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IT chatbot phía backend (IT-BOT-xx): validation, phiên khách/đăng nhập, quyền sở hữu phiên, lịch
 * sử, câu mẫu của luật và hành vi khi module AI (chatbot-api) SẬP — trỏ chatbot-api tới cổng chết.
 * Chất lượng câu trả lời AI thật được kiểm ở {@code TenantChatbotE2EIT} trên stack docker.
 */
@TestPropertySource(properties = {
        "app.ai.chatbot-api.enabled=true",
        "app.ai.chatbot-api.base-url=http://127.0.0.1:9",
        "app.ai.chatbot-api.connect-timeout=PT1S",
        "app.ai.chatbot-api.request-timeout=PT2S"})
class TenantChatbotIT extends TenantItSupport {

    @Test
    @DisplayName("IT-BOT-01 AI sập + câu tìm phòng → 200, câu báo lỗi thân thiện, không trả phòng, không lỗi 500")
    void aiDownFindRoom() throws Exception {
        Actor tenant = newTenant();
        JsonNode res = ask(tenant, "tìm phòng trọ ở Cầu Giấy dưới 3 triệu", null, null, 200);
        assertTrue(res.at("/data/reply").asText().contains("gặp sự cố"), res::toString);
        assertEquals(0, res.at("/data/listings").size());
        // Lượt hỏi vẫn được lưu vào lịch sử.
        long conv = res.at("/data/conversationId").asLong();
        assertEquals(2, call(HttpMethod.GET, "/api/ai/chatbot/conversations/" + conv + "/messages", tenant, null, 200)
                .at("/data/totalElements").asInt());
    }

    @Test
    @DisplayName("IT-BOT-02 AI sập vẫn trả lời câu mẫu: chào hỏi (GREETING), thuật ngữ 'cọc là gì' (GLOSSARY), hướng dẫn đăng tin")
    void ruleRepliesWorkWhenAiDown() throws Exception {
        Actor tenant = newTenant();
        assertEquals("GREETING", ask(tenant, "xin chào", null, null, 200).at("/data/intent").asText());
        assertEquals("GLOSSARY", ask(tenant, "cọc là gì", null, null, 200).at("/data/intent").asText());
        assertEquals("HOW_TO_POST", ask(tenant, "làm sao để đăng tin cho thuê", null, null, 200).at("/data/intent").asText());
    }

    @Test
    @DisplayName("IT-BOT-03 Nội dung nhạy cảm → SENSITIVE, flaggedForReview=true, câu từ chối")
    void sensitiveRefused() throws Exception {
        JsonNode res = ask(newTenant(), "tìm phòng có bán ma túy", null, null, 200);
        assertEquals("SENSITIVE", res.at("/data/intent").asText());
        assertTrue(res.at("/data/flaggedForReview").asBoolean());
    }

    @Test
    @DisplayName("IT-BOT-04 Validation: rỗng / >500 ký tự → 400; khách không có sessionId → 400; khách có sessionId → 200")
    void validation() throws Exception {
        Actor tenant = newTenant();
        ask(tenant, "   ", null, null, 400);
        ask(tenant, "a".repeat(501), null, null, 400);
        JsonNode noSession = ask(null, "xin chào", null, null, 400);
        assertEquals("VALIDATION_FAILED", errorCode(noSession));
        ask(null, "xin chào", null, UUID.randomUUID().toString(), 200);
    }

    @Test
    @DisplayName("IT-BOT-05 Phiên của người khác: gửi tiếp / đọc lịch sử → 403; khách khác sessionId → 403; lịch sử của mình đọc được")
    void conversationOwnership() throws Exception {
        Actor t1 = newTenant();
        Actor t2 = newTenant();
        long conv = ask(t1, "xin chào", null, null, 200).at("/data/conversationId").asLong();

        ask(t2, "xin chào", conv, null, 403);
        call(HttpMethod.GET, "/api/ai/chatbot/conversations/" + conv + "/messages", t2, null, 403);
        ask(null, "xin chào", conv, UUID.randomUUID().toString(), 403);

        ask(t1, "cọc là gì", conv, null, 200);
        assertEquals(4, call(HttpMethod.GET, "/api/ai/chatbot/conversations/" + conv + "/messages", t1, null, 200)
                .at("/data/totalElements").asInt());
        assertTrue(call(HttpMethod.GET, "/api/ai/chatbot/conversations", t1, null, 200).at("/data/totalElements").asInt() >= 1);
    }

    @Test
    @DisplayName("IT-BOT-06 Vượt 30 câu/phút → 429 CHATBOT_RATE_LIMIT")
    void rateLimit() throws Exception {
        Actor tenant = newTenant();
        int last = 0;
        for (int i = 0; i < 31; i++) {
            last = raw(HttpMethod.POST, "/api/ai/chatbot/message", tenant, Map.of("message", "cọc là gì")).getResponse().getStatus();
        }
        assertEquals(429, last);
    }

    @Test
    @DisplayName("IT-BOT-07 Admin tắt chatbot (ai.chatbot.enabled=false) → 503 AI_MODULE_DISABLED")
    void disabledByAdmin() throws Exception {
        Actor tenant = newTenant();
        try {
            sql("update system_configs set config_value = 'false' where config_key = 'ai.chatbot.enabled'");
            resetRedis();
            JsonNode res = ask(tenant, "xin chào", null, null, 503);
            assertEquals("AI_MODULE_DISABLED", errorCode(res));
        } finally {
            sql("update system_configs set config_value = 'true' where config_key = 'ai.chatbot.enabled'");
            resetRedis();
        }
    }

    @Test
    @DisplayName("IT-BOT-08 [BUG-18] Trình duyệt dùng chung: A dùng chatbot rồi đăng xuất → khách/B dùng chatbot phải được (không 403)")
    void sharedBrowserSession() throws Exception {
        String browserSession = UUID.randomUUID().toString();
        Actor a = newTenant();
        Actor b = newTenant();
        ask(a, "xin chào", null, browserSession, 200);

        ask(null, "xin chào", null, browserSession, 200);
        ask(b, "xin chào", null, browserSession, 200);
    }

    @Test
    @DisplayName("IT-BOT-09 [BUG-18b] Khách chat rồi đăng nhập (cùng sessionId) → phiên phải vào lịch sử tài khoản và đọc lại được")
    void guestConversationAttachedAfterLogin() throws Exception {
        String browserSession = UUID.randomUUID().toString();
        long conv = ask(null, "xin chào", null, browserSession, 200).at("/data/conversationId").asLong();
        Actor t = newTenant();
        ask(t, "cọc là gì", null, browserSession, 200);

        call(HttpMethod.GET, "/api/ai/chatbot/conversations/" + conv + "/messages", t, null, 200);
    }

    @Test
    @DisplayName("IT-BOT-10 Câu hỏi chứa HTML → lưu lịch sử đã lọc thẻ")
    void htmlStrippedInHistory() throws Exception {
        Actor tenant = newTenant();
        long conv = ask(tenant, "<b>cọc</b> là gì", null, null, 200).at("/data/conversationId").asLong();
        JsonNode hist = call(HttpMethod.GET, "/api/ai/chatbot/conversations/" + conv + "/messages", tenant, null, 200);
        assertFalse(hist.toString().contains("<b>"));
    }

    private JsonNode ask(Actor actor, String message, Long conversationId, String sessionId, int status) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("message", message);
        if (conversationId != null) {
            body.put("conversationId", conversationId);
        }
        if (sessionId != null) {
            body.put("sessionId", sessionId);
        }
        return call(HttpMethod.POST, "/api/ai/chatbot/message", actor, body, status);
    }
}
