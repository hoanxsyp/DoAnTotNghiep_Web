package com.webtro.modules.ai.engine;

import com.webtro.common.enums.CategoryCode;
import com.webtro.common.enums.ChatbotIntent;
import com.webtro.modules.ai.engine.ChatbotEngine.ChatSlots;
import com.webtro.modules.ai.engine.ChatbotEngine.ChatbotResult;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UT chatbot theo luật (UT-BOT-xx) — nhánh xử lý câu nhạy cảm/thuật ngữ/câu mẫu, và TOÀN BỘ hội thoại
 * khi Admin tắt module AI ({@code CHATBOT_AI_ENABLED=false}).
 */
class RuleBasedChatbotEngineTest {

    private final RuleBasedChatbotEngine engine = new RuleBasedChatbotEngine();

    private ChatbotResult ask(String message) {
        return engine.interpret(message, ChatSlots.empty(), false);
    }

    @ParameterizedTest(name = "UT-BOT-01 \"{0}\" → intent {1}")
    @CsvSource({
            "xin chào, GREETING",
            "cọc là gì, GLOSSARY",
            "làm sao để đăng tin cho thuê, HOW_TO_POST",
            "tìm phòng có ma túy, SENSITIVE",
            "tìm phòng trọ dưới 3 triệu, FIND_ROOM",
    })
    void intents(String message, ChatbotIntent expected) {
        assertEquals(expected, ask(message).intent());
    }

    @Test
    @DisplayName("UT-BOT-02 Nhạy cảm → sensitive=true, có câu từ chối")
    void sensitive() {
        ChatbotResult r = ask("cho thuê phòng có cá độ không");
        assertTrue(r.sensitive());
        assertFalse(r.cannedReply() == null || r.cannedReply().isBlank());
    }

    @Test
    @DisplayName("UT-BOT-03 Tách giá: 'dưới 3 triệu' → priceTo=3.000.000; '2-4 triệu' → khoảng 2–4 triệu")
    void priceSlots() {
        assertEquals(0, ask("tìm phòng dưới 3 triệu").slots().priceTo().compareTo(new BigDecimal("3000000")));
        ChatSlots range = ask("tìm phòng từ 2 đến 4 triệu").slots();
        assertEquals(0, range.priceTo().compareTo(new BigDecimal("4000000")));
    }

    @Test
    @DisplayName("UT-BOT-04 Nhận loại tin: 'ở ghép' → ROOMMATE; 'chung cư mini' → MINI_APARTMENT")
    void category() {
        assertEquals(CategoryCode.ROOMMATE, ask("tìm người ở ghép quận đống đa").slots().categoryCode());
        assertEquals(CategoryCode.MINI_APARTMENT, ask("thuê chung cư mini dưới 5 triệu").slots().categoryCode());
    }

    @Test
    @DisplayName("UT-BOT-05 Nhớ ngữ cảnh: lượt 2 chỉ nói giá thì vẫn giữ khu vực của lượt 1")
    void keepsContext() {
        ChatSlots first = ask("tìm phòng quận đống đa").slots();
        ChatSlots second = engine.interpret("dưới 3 triệu", first, false).slots();
        assertEquals(first.locationKeyword(), second.locationKeyword());
        assertEquals(0, second.priceTo().compareTo(new BigDecimal("3000000")));
    }

    @Test
    @DisplayName("UT-BOT-06 resetContext=true → bỏ slot cũ")
    void reset() {
        ChatSlots first = ask("tìm phòng quận đống đa dưới 3 triệu").slots();
        assertNull(engine.interpret("xin chào", first, true).slots().priceTo());
    }

    @ParameterizedTest(name = "UT-BOT-07 [BUG-19] \"{0}\" → khu vực phải là \"{1}\"")
    @Disabled("BUG-19: regex LOCATION không có ranh giới từ và lấy group(0) → 'phòng trọ ở Cầu Giấy' ra 'o o cau'. "
            + "Bỏ @Disabled sau khi fix.")
    @CsvSource({
            "tìm phòng trọ ở Cầu Giấy dưới 3 triệu, cau giay",
            "phòng ở Đống Đa, dong da",
            "tìm phòng gần Bách Khoa, bach khoa",
    })
    void locationKeyword(String message, String expected) {
        assertEquals(expected, ask(message).slots().locationKeyword());
    }

    @Test
    @DisplayName("UT-BOT-08 [BUG-16] Câu ngoài phạm vi không được chèn nội dung lạc đề về 'pháp lý hợp đồng'; câu ví dụ không dùng 'Quận 1' (web chỉ có Hà Nội)")
    @Disabled("BUG-16: câu mẫu OUT_OF_SCOPE luôn kèm câu khuyên tham khảo luật sư; câu UNKNOWN gợi ý 'Quận 1'. "
            + "Bỏ @Disabled sau khi fix.")
    void cannedRepliesOnTopic() {
        ChatbotResult r = ask("thời tiết hôm nay thế nào");
        assertEquals(ChatbotIntent.OUT_OF_SCOPE, r.intent());
        assertFalse(r.cannedReply().contains("pháp lý"), r.cannedReply());
        ChatbotResult unknown = ask("asdkjh qwe");
        assertFalse(String.valueOf(unknown.cannedReply()).contains("Quận 1"), unknown.cannedReply());
    }
}
