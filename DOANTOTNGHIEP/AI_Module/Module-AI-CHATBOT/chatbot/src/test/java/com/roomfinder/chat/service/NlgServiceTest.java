package com.roomfinder.chat.service;

import com.roomfinder.chat.config.LlmProperties;
import com.roomfinder.chat.domain.Room;
import com.roomfinder.chat.llm.LlmClient;
import com.roomfinder.chat.model.Filters;
import com.roomfinder.chat.model.NlgOutcome;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Kiểm thử prompt NLG (§6.1) — LLM giả lập, bắt lại nội dung gửi đi. */
class NlgServiceTest {

    /** LLM giả: ghi lại prompt, trả câu hợp lệ (đúng mã phòng có trong context). */
    private static final class CapturingLlm implements LlmClient {
        final List<String> systems = new ArrayList<>();
        final List<String> users = new ArrayList<>();

        @Override
        public String complete(String system, String user, double temperature) {
            systems.add(system);
            users.add(user);
            return "Mình gợi ý phòng [#7] nhé.";
        }
    }

    private final CapturingLlm llm = new CapturingLlm();
    private final NlgService nlg = new NlgService(llm, new HallucinationValidator(), new LlmProperties());

    private Room listing(String description, List<String> amenities) {
        Room r = new Room();
        r.setId(7L);
        r.setPrice(2_500_000L);
        r.setArea(BigDecimal.valueOf(20));
        r.setAddressText("Phường Dịch Vọng, Quận Cầu Giấy");
        r.setDescription(description);
        r.setAmenityNames(amenities);
        r.setHasParking(true);
        r.setIsPrivateBathroom(true);
        return r;
    }

    @Test
    void promptListsRealAmenitiesAndShortDescription() {
        String longDesc = "Phòng mới xây, yên tĩnh, thoáng mát. ".repeat(20);
        NlgOutcome out = nlg.generate("tư vấn giúp mình", List.of(listing(longDesc, List.of("Máy lạnh", "Thang máy"))),
                new Filters(), null);

        assertTrue(out.valid());
        String user = llm.users.get(0);
        assertTrue(user.contains("[#7]"), user);
        assertTrue(user.contains("Tiện ích: Máy lạnh, Thang máy"), user);
        assertTrue(user.contains("Vệ sinh khép kín: Có"), user);
        assertTrue(user.contains("Mô tả: Phòng mới xây"), user);
        assertFalse(user.contains("Điều hòa: "), user);   // tin của web: không dùng 4 cờ của phòng demo
        String descLine = user.lines().filter(l -> l.contains("Mô tả:")).findFirst().orElseThrow();
        assertTrue(descLine.length() < 230 && descLine.endsWith("…"), descLine);
        assertTrue(llm.systems.get(0).contains("Tiện ích"), "system prompt phải cấm bịa tiện ích");
    }

    @Test
    void listingWithoutAmenitiesSaysSoInsteadOfGuessing() {
        nlg.generate("q", List.of(listing(null, List.of())), new Filters(), null);
        String user = llm.users.get(0);
        assertTrue(user.contains("Tiện ích: (tin đăng không ghi)"), user);
        assertFalse(user.contains("Mô tả:"), user);
    }

    @Test
    void demoRoomKeepsLegacyFlags() {
        Room demo = listing("mô tả ngắn", null);   // nguồn db: amenityNames null
        demo.setHasAirConditioner(true);
        nlg.generate("q", List.of(demo), new Filters(), null);
        assertTrue(llm.users.get(0).contains("Điều hòa: Có"));
    }

    @Test
    void shortDescriptionCutsAtWordBoundary() {
        assertEquals("ngắn gọn", NlgService.shortDescription("  ngắn \n gọn "));
        String cut = NlgService.shortDescription("chữ ".repeat(100));
        assertTrue(cut.endsWith("chữ…") && cut.length() <= 202, cut);
        assertEquals(null, NlgService.shortDescription("  "));
    }
}
