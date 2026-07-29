package com.webtro.modules.ai.engine;

import com.webtro.common.enums.CategoryCode;
import com.webtro.common.enums.ChatbotIntent;
import com.webtro.common.enums.CurfewType;
import com.webtro.common.enums.FurnitureStatus;
import com.webtro.common.enums.GenderRequirement;

import java.math.BigDecimal;
import java.util.List;

/**
 * Bộ hội thoại chatbot theo luật (canonical mục 10.3, §9.3, §3.15). Đặt sau interface để thay
 * implementation. Hiện thực chốt: {@link RuleBasedChatbotEngine} — phân loại intent (từ khóa/regex
 * có trọng số) + slot filling.
 *
 * <p>Engine <b>không</b> truy vấn DB và <b>không</b> bịa thông tin tin đăng — chỉ hiểu câu hỏi và
 * trích slot. Việc tra tin công khai do tầng service gọi {@code ListingSearchService}.
 */
public interface ChatbotEngine {

    String version();

    /** Slot đã trích (tất cả nullable — bổ sung dần qua các lượt hỏi lại). */
    record ChatSlots(
            BigDecimal priceFrom,
            BigDecimal priceTo,
            BigDecimal areaFrom,
            BigDecimal areaTo,
            Integer maxOccupants,
            GenderRequirement genderRequirement,
            FurnitureStatus furnitureStatus,
            CurfewType curfewType,
            Boolean petAllowed,
            Boolean parkingAvailable,
            CategoryCode categoryCode,
            String locationKeyword) {

        public static ChatSlots empty() {
            return new ChatSlots(null, null, null, null, null, null, null, null, null, null, null, null);
        }
    }

    /**
     * Kết quả hiểu một tin nhắn.
     *
     * @param intent               intent nhận diện
     * @param confidence           độ tin cậy intent [0, 1]
     * @param slots                slot đã gộp (prior + mới trích)
     * @param missingImportantSlots slot quan trọng còn thiếu ("location"/"priceTo"/"maxOccupants")
     * @param cannedReply          câu trả lời sẵn (GREETING/HOW_TO_POST/FAQ/OUT_OF_SCOPE/SENSITIVE);
     *                             {@code null} với FIND_ROOM (service tự dựng theo kết quả)
     * @param glossaryTerm         thuật ngữ tra được (intent GLOSSARY); {@code null} nếu không
     * @param sensitive            câu hỏi nhạy cảm (cờ để service đánh dấu review)
     */
    record ChatbotResult(
            ChatbotIntent intent,
            BigDecimal confidence,
            ChatSlots slots,
            List<String> missingImportantSlots,
            String cannedReply,
            String glossaryTerm,
            boolean sensitive) {
    }

    /**
     * Hiểu một tin nhắn, gộp với slot đã thu thập.
     *
     * @param message   câu người dùng (đã sanitize)
     * @param prior     slot đã thu thập ở các lượt trước (có thể {@code null})
     * @param resetContext bỏ slot cũ, bắt đầu lại nhu cầu
     */
    ChatbotResult interpret(String message, ChatSlots prior, boolean resetContext);
}
