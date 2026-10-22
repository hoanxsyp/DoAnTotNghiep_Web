package com.webtro.modules.ai.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webtro.common.PageResponse;
import com.webtro.common.enums.ChatbotConversationStatus;
import com.webtro.common.enums.ChatbotIntent;
import com.webtro.common.enums.ChatbotSender;
import com.webtro.constant.ConfigKey;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessException;
import com.webtro.exception.ForbiddenException;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.ai.dto.request.ChatbotMessageRequest;
import com.webtro.modules.ai.dto.response.ChatbotConversationResponse;
import com.webtro.modules.ai.dto.response.ChatbotMessageHistoryResponse;
import com.webtro.modules.ai.dto.response.ChatbotMessageResponse;
import com.webtro.modules.ai.dto.response.ChatbotMessageResponse.ChatbotListingItem;
import com.webtro.modules.ai.engine.AiChatbotClient;
import com.webtro.modules.ai.engine.AiChatbotClient.AiReply;
import com.webtro.modules.ai.engine.AiChatbotClient.AiRoom;
import com.webtro.modules.ai.engine.AiModuleSupport;
import com.webtro.modules.ai.engine.ChatbotEngine;
import com.webtro.modules.ai.engine.ChatbotEngine.ChatSlots;
import com.webtro.modules.ai.engine.ChatbotEngine.ChatbotResult;
import com.webtro.modules.ai.entity.ChatbotConversation;
import com.webtro.modules.ai.entity.ChatbotMessage;
import com.webtro.modules.ai.mapper.ChatbotMapper;
import com.webtro.modules.ai.repository.ChatbotConversationRepository;
import com.webtro.modules.ai.repository.ChatbotMessageRepository;
import com.webtro.modules.ai.service.ChatbotService;
import com.webtro.modules.listing.dto.response.ListingSummaryResponse;
import com.webtro.modules.search.dto.request.ListingSearchRequest;
import com.webtro.modules.search.service.ListingSearchService;
import com.webtro.security.CustomUserDetails;
import com.webtro.security.RateLimitService;
import com.webtro.util.HtmlSanitizer;
import com.webtro.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hiện thực nghiệp vụ chatbot (canonical mục 10.3, §9.3, §3.15). Ràng buộc cứng: chỉ trả tin công
 * khai, không bịa thông tin, luôn kèm disclaimer khi trả tin, hỏi lại tối đa
 * {@code ai.chatbot.max_clarify_turns}.
 *
 * <p>Câu hỏi tìm phòng do module AI chatbot trả lời ({@link AiChatbotClient}: ViSoBERT + tin thật
 * qua {@code /api/search/listings} → vẫn đúng {@code publicStatuses()}, + Gemini có guardrail).
 * Chatbot theo luật ({@link ChatbotEngine}) chỉ còn lo nhạy cảm, thuật ngữ, câu mẫu chào hỏi /
 * đăng tin / FAQ, và toàn bộ hội thoại khi tắt {@code app.ai.chatbot-api.enabled}. AI sập →
 * báo lỗi, không tìm phòng bằng luật.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatbotServiceImpl implements ChatbotService {

    private static final int RESULT_LIMIT = 5;
    private static final String DISCLAIMER =
            "Thông tin lấy trực tiếp từ tin đăng. Bạn vui lòng liên hệ chủ trọ để xác nhận phòng còn trống.";

    private final ChatbotEngine engine;
    private final AiChatbotClient aiChatbot;
    private final ChatbotConversationRepository conversationRepository;
    private final ChatbotMessageRepository messageRepository;
    private final ListingSearchService listingSearchService;
    private final SystemConfigService config;
    private final RateLimitService rateLimitService;
    private final ChatbotMapper mapper;
    private final AiModuleSupport support;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ChatbotMessageResponse handleMessage(ChatbotMessageRequest request, CustomUserDetails principal) {
        support.ensureEnabled(ConfigKey.AI_CHATBOT_ENABLED);
        long t0 = System.nanoTime();

        Long userId = principal == null ? null : principal.getId();
        String sessionId = request.getSessionId();
        if (userId == null && (sessionId == null || sessionId.isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Cần sessionId khi chưa đăng nhập");
        }

        int maxLength = config.getInt(ConfigKey.CHATBOT_MESSAGE_MAX_LENGTH);
        String sanitized = HtmlSanitizer.stripAllHtml(request.getMessage()).trim();
        if (sanitized.isEmpty()) {
            throw new BusinessException(ErrorCode.CHATBOT_MESSAGE_EMPTY);
        }
        if (sanitized.length() > maxLength) {
            throw new BusinessException(ErrorCode.CHATBOT_MESSAGE_TOO_LONG,
                    "Câu hỏi tối đa " + maxLength + " ký tự");
        }

        // Chống spam: 30/phút theo user hoặc session.
        String rlId = userId != null ? "u:" + userId : "s:" + sessionId;
        try {
            rateLimitService.checkLimit("chatbot", rlId,
                    config.getInt(ConfigKey.SPAM_CHATBOT_PER_MINUTE), Duration.ofMinutes(1));
        } catch (com.webtro.exception.RateLimitException e) {
            throw new BusinessException(ErrorCode.CHATBOT_RATE_LIMIT, e.getMessage());
        }

        int maxClarify = config.getInt(ConfigKey.AI_CHATBOT_MAX_CLARIFY_TURNS);
        boolean reset = Boolean.TRUE.equals(request.getResetContext());

        ChatbotConversation conv = resolveConversation(request, userId, sessionId, reset);
        ChatSlots prior = reset ? ChatSlots.empty() : parseSlots(conv.getCollectedFilters());

        ChatbotResult result = support.runWithTimeout("chatbot", ConfigKey.AI_CHATBOT_TIMEOUT_MS,
                () -> engine.interpret(sanitized, prior, reset));

        // ------------------------------------------------------------------
        //  Định tuyến: luật cũ (chỉ nhạy cảm / thuật ngữ / câu mẫu) ↔ chatbot AI (mọi câu khác)
        // ------------------------------------------------------------------
        // 1) Nhạy cảm → từ chối ngay, KHÔNG gửi sang AI (kiểm duyệt ưu tiên tuyệt đối, §9.3).
        // 2) Thuật ngữ tra được trong từ điển → định nghĩa có sẵn.
        // 3) Còn lại → chatbot AI. Chỉ khi AI coi là ngoài phạm vi / chỉ hỏi lại MÀ luật nhận ra
        //    chào hỏi / đăng tin / FAQ thì dùng câu mẫu của luật (AI chỉ được dạy chuyện tìm phòng).
        //    Luật KHÔNG quyết định trước: từ khóa "phí", "cho thuê"… chặn nhầm câu hỏi về phòng.
        AiReply ai = null;
        boolean aiUnavailable = false;
        boolean handledByRule = result.intent() == ChatbotIntent.SENSITIVE
                || (result.intent() == ChatbotIntent.GLOSSARY && result.glossaryTerm() != null
                    && asksDefinition(sanitized));
        if (aiChatbot.isEnabled() && !handledByRule) {
            if (reset) {
                aiChatbot.reset(conv.getSessionId());
            }
            try {
                ai = aiChatbot.chat(conv.getSessionId(), sanitized, userId);
            } catch (AiChatbotClient.Unavailable e) {
                aiUnavailable = true;
                log.warn("Chatbot AI không phản hồi (conversation {}): {}", conv.getId(), e.getMessage());
            }
            if (ai != null && preferRuleReply(result, ai)) {
                ai = null;
            }
        }

        List<ChatbotListingItem> listings = new ArrayList<>();
        int totalResults = 0;
        String reply;
        List<String> missing = result.missingImportantSlots();
        String searchUrl = null;
        List<ChatbotMessageResponse.ExpansionSuggestion> expansions = null;
        boolean fallback = false;
        ChatbotIntent intent = result.intent();
        BigDecimal confidence = result.confidence();
        String turnSlotsJson;

        if (ai != null) {
            // ----- Câu trả lời của chatbot AI (tin đăng thật, đã lọc tin công khai phía backend) -----
            intent = "out_of_scope".equals(ai.intent()) ? ChatbotIntent.OUT_OF_SCOPE : ChatbotIntent.FIND_ROOM;
            confidence = ai.confidence() == null ? null
                    : ai.confidence().max(BigDecimal.ZERO).min(BigDecimal.ONE).setScale(3, RoundingMode.HALF_UP);
            reply = ai.reply();
            for (AiRoom room : ai.rooms()) {
                listings.add(toItem(room));
            }
            totalResults = listings.size();
            boolean clarify = "CLARIFY".equals(ai.path());
            missing = clarify ? List.of("location", "priceTo") : List.of();
            if (clarify && conv.getClarifyTurnCount() < maxClarify) {
                conv.setClarifyTurnCount(conv.getClarifyTurnCount() + 1);
            }
            if (intent == ChatbotIntent.FIND_ROOM && !clarify && listings.isEmpty()) {
                expansions = defaultExpansions();
                fallback = true;
            }
            if (intent == ChatbotIntent.OUT_OF_SCOPE) {
                fallback = true;
            }
            searchUrl = listings.isEmpty() ? null : buildSearchUrl(slotsFromAi(ai.activeFilters()));
            turnSlotsJson = toJson(aiTurnInfo(ai));
        } else if (aiUnavailable && !RULE_REPLY_INTENTS.contains(result.intent())) {
            // ----- AI sập/timeout: báo lỗi, KHÔNG tìm phòng bằng luật (quyết định 2026-10-17) -----
            reply = "Xin lỗi, trợ lý tìm phòng đang gặp sự cố nên chưa trả lời được. Bạn thử lại sau ít phút, "
                    + "hoặc dùng trang Tìm kiếm để lọc phòng nhé.";
            missing = List.of();
            fallback = true;
            turnSlotsJson = toJson(Map.of("engine", "AI", "error", "UNAVAILABLE"));
        } else if (result.intent() == ChatbotIntent.FIND_ROOM) {
            // ----- Chatbot theo luật (chỉ khi tắt app.ai.chatbot-api.enabled) -----
            ListingSearchRequest sreq = toSearchRequest(result.slots());
            Page<ListingSummaryResponse> page = searchListings(sreq, principal);
            totalResults = (int) page.getTotalElements();
            searchUrl = buildSearchUrl(result.slots());

            boolean canClarify = !missing.isEmpty() && conv.getClarifyTurnCount() < maxClarify;
            if (totalResults == 0) {
                reply = "Rất tiếc, mình chưa tìm thấy phòng nào khớp yêu cầu. Bạn thử nới giá, mở rộng "
                        + "khu vực hoặc chấp nhận diện tích nhỏ hơn nhé.";
                expansions = defaultExpansions();
                fallback = true;
            } else if (canClarify) {
                conv.setClarifyTurnCount(conv.getClarifyTurnCount() + 1);
                reply = "Mình tìm được " + totalResults + " tin. Để lọc chính xác hơn, bạn cho mình biết "
                        + describeMissing(missing) + " nhé?";
            } else {
                for (ListingSummaryResponse it : page.getContent()) {
                    listings.add(toItem(it));
                }
                reply = "Mình tìm được " + totalResults + " tin phù hợp. Bạn xem thử nhé:";
            }
            turnSlotsJson = toJson(result.slots());
        } else {
            // ----- Câu mẫu của luật: nhạy cảm / thuật ngữ / chào hỏi / đăng tin / FAQ -----
            reply = result.cannedReply();
            missing = List.of();
            fallback = result.intent() == ChatbotIntent.UNKNOWN || result.intent() == ChatbotIntent.OUT_OF_SCOPE;
            turnSlotsJson = toJson(result.slots());
        }

        // Lưu hội thoại + hai tin nhắn. collected_filters luôn giữ slot của luật (ChatSlots) — ngữ
        // cảnh hội thoại của chatbot AI nằm ở chính module (Redis), không trộn vào đây.
        conv.setLastIntent(intent);
        conv.setCollectedFilters(toJson(result.slots()));
        conv.setLastMessageAt(Instant.now());
        conv.setMessageCount(conv.getMessageCount() + 2);
        conversationRepository.save(conv);

        // Tin của USER: mang intent (BOT phải null theo ck_chatbot_messages_bot_intent).
        ChatbotMessage userMsg = ChatbotMessage.builder()
                .conversation(conv)
                .sender(ChatbotSender.USER)
                .content(sanitized)
                .intent(intent)
                .intentConfidence(confidence)
                .extractedSlots(turnSlotsJson)
                .isFallback(false)
                .build();
        messageRepository.save(userMsg);

        List<Long> listingIds = listings.stream().map(ChatbotListingItem::getId).toList();
        int responseMs = (int) ((System.nanoTime() - t0) / 1_000_000);
        ChatbotMessage botMsg = ChatbotMessage.builder()
                .conversation(conv)
                .sender(ChatbotSender.BOT)
                .content(truncate(reply, 2000))
                .intent(null)
                .extractedSlots(turnSlotsJson)
                .resultListingIds(toJson(listingIds))
                .resultCount(totalResults)
                .isFallback(fallback)
                .responseMs(responseMs)
                .build();
        messageRepository.save(botMsg);

        return ChatbotMessageResponse.builder()
                .conversationId(conv.getId())
                .messageId(botMsg.getId())
                .intent(intent)
                .intentConfidence(confidence)
                .reply(reply)
                .extractedSlots(parseMap(turnSlotsJson))
                .missingSlots(missing)
                .clarifyTurn(conv.getClarifyTurnCount())
                .maxClarifyTurns(maxClarify)
                .listings(listings)
                .totalResults(totalResults)
                .searchUrl(searchUrl)
                .expansionSuggestions(expansions)
                .glossaryTerm(ai == null ? result.glossaryTerm() : null)
                .disclaimer(listings.isEmpty() ? null : DISCLAIMER)
                .flaggedForReview(result.sensitive())
                .createdAt(botMsg.getCreatedAt())
                .build();
    }

    /**
     * Câu thật sự hỏi NGHĨA của thuật ngữ ("cọc là gì"). Luật xếp GLOSSARY cho mọi câu chứa thuật
     * ngữ — "phòng này cọc bao nhiêu" sẽ nhận định nghĩa của "cọc" thay vì câu trả lời về phòng.
     */
    private static boolean asksDefinition(String message) {
        String t = TextNormalizer.removeAccents(message.toLowerCase());
        return t.contains("la gi") || t.contains("nghia la") || t.contains("co nghia") || t.contains("y nghia");
    }

    /** Intent mà câu mẫu của luật trả lời tốt hơn AI (AI chỉ được dạy chuyện tìm phòng). */
    private static final Set<ChatbotIntent> RULE_REPLY_INTENTS = Set.of(
            ChatbotIntent.GREETING, ChatbotIntent.HOW_TO_POST, ChatbotIntent.FAQ, ChatbotIntent.OUT_OF_SCOPE);

    /** Intent tìm phòng của AI — câu trả lời của AI luôn được giữ (kể cả khi 0 kết quả). */
    private static final Set<String> AI_SEARCH_INTENTS = Set.of("search_room", "refine_search");

    /**
     * Dùng câu mẫu của luật khi luật nhận ra chào hỏi/đăng tin/FAQ/ngoài phạm vi VÀ chatbot AI
     * không có gì cụ thể để nói: ngoài phạm vi, chỉ hỏi lại khu vực/giá, hoặc không trả tin nào
     * mà cũng không phải câu tìm phòng. ViSoBERT chỉ được dạy chuyện tìm phòng — "làm sao để đăng
     * tin cho thuê" bị xếp nhầm vào room_detail và hỏi lại "phòng nào?".
     */
    private static boolean preferRuleReply(ChatbotResult rule, AiReply ai) {
        if (!RULE_REPLY_INTENTS.contains(rule.intent())) {
            return false;
        }
        return "out_of_scope".equals(ai.intent())
                || "CLARIFY".equals(ai.path())
                || (ai.rooms().isEmpty() && !AI_SEARCH_INTENTS.contains(ai.intent()));
    }

    /** Thông tin kỹ thuật của lượt AI — lưu vào extracted_slots để trang admin "Xem log AI" thấy. */
    private static Map<String, Object> aiTurnInfo(AiReply ai) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("engine", "AI");
        m.put("aiIntent", ai.intent());
        m.put("path", ai.path());
        m.put("relaxed", ai.relaxed());
        m.put("rankedBy", ai.rankedBy());
        m.put("hallucinationDetected", ai.hallucinationDetected());
        m.put("activeFilters", ai.activeFilters());
        return m;
    }

    /** Bộ lọc của chatbot AI → ChatSlots, chỉ để dựng link "xem thêm trên trang Tìm kiếm". */
    private static ChatSlots slotsFromAi(Map<String, Object> f) {
        return new ChatSlots(decimal(f.get("price_min")), decimal(f.get("price_max")),
                decimal(f.get("area_min")), null, null, null, null, null, null, null, null,
                f.get("location") instanceof String s ? s : null);
    }

    private static BigDecimal decimal(Object v) {
        return v instanceof Number n ? new BigDecimal(n.toString()) : null;
    }

    private ChatbotListingItem toItem(AiRoom r) {
        return ChatbotListingItem.builder()
                .id(r.id())
                .slug(r.slug())
                .title(r.title())
                .price(r.price())
                .area(r.area())
                .shortAddress(r.address())
                .thumbnailUrl(r.thumbnailUrl())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ChatbotConversationResponse> listConversations(Long userId, Pageable pageable) {
        Page<ChatbotConversation> page = conversationRepository
                .findByUserIdAndDeletedAtIsNullOrderByStartedAtDesc(userId, pageable);
        return PageResponse.from(page, c -> mapper.toConversationResponse(c, null));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ChatbotMessageHistoryResponse> listMessages(Long conversationId, Long userId,
                                                                    Pageable pageable) {
        ChatbotConversation conv = conversationRepository.findById(conversationId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CHATBOT_CONVERSATION_NOT_FOUND));
        if (conv.getUserId() == null || !conv.getUserId().equals(userId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        Page<ChatbotMessage> page = messageRepository
                .findByConversation_IdOrderByCreatedAtDesc(conversationId, pageable);
        return PageResponse.from(page, mapper::toMessageHistoryResponse);
    }

    // ------------------------------------------------------------------
    //  Hỗ trợ
    // ------------------------------------------------------------------

    private ChatbotConversation resolveConversation(ChatbotMessageRequest request, Long userId,
                                                    String sessionId, boolean reset) {
        if (request.getConversationId() != null) {
            ChatbotConversation conv = conversationRepository.findById(request.getConversationId())
                    .filter(c -> c.getDeletedAt() == null)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CHATBOT_CONVERSATION_NOT_FOUND));
            assertOwnership(conv, userId, sessionId);
            if (reset) {
                conv.setClarifyTurnCount(0);
            }
            return conv;
        }
        if (sessionId != null && !sessionId.isBlank()) {
            var existing = conversationRepository.findBySessionIdAndDeletedAtIsNull(sessionId);
            if (existing.isPresent()) {
                ChatbotConversation conv = existing.get();
                assertOwnership(conv, userId, sessionId);
                return conv;
            }
        }
        return createConversation(userId, sessionId);
    }

    private ChatbotConversation createConversation(Long userId, String sessionId) {
        String sid = (sessionId == null || sessionId.isBlank()) ? UUID.randomUUID().toString() : sessionId;
        ChatbotConversation conv = ChatbotConversation.builder()
                .userId(userId)
                .sessionId(sid)
                .status(ChatbotConversationStatus.ACTIVE)
                .collectedFilters(toJson(ChatSlots.empty()))
                .clarifyTurnCount(0)
                .messageCount(0)
                .build();
        return conversationRepository.save(conv);
    }

    private void assertOwnership(ChatbotConversation conv, Long userId, String sessionId) {
        if (conv.getUserId() != null) {
            if (!conv.getUserId().equals(userId)) {
                throw new ForbiddenException(ErrorCode.FORBIDDEN);
            }
        } else if (sessionId == null || !sessionId.equals(conv.getSessionId())) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
    }

    private Page<ListingSummaryResponse> searchListings(ListingSearchRequest req, CustomUserDetails principal) {
        PageResponse<ListingSummaryResponse> pr = listingSearchService.search(
                req, PageRequest.of(0, RESULT_LIMIT), principal);
        return new org.springframework.data.domain.PageImpl<>(
                pr.getItems(), PageRequest.of(pr.getPage(), Math.max(1, pr.getSize())), pr.getTotalElements());
    }

    private ListingSearchRequest toSearchRequest(ChatSlots s) {
        return ListingSearchRequest.builder()
                .keyword(s.locationKeyword())
                .priceFrom(s.priceFrom())
                .priceTo(s.priceTo())
                .areaFrom(s.areaFrom())
                .areaTo(s.areaTo())
                .maxOccupants(s.maxOccupants())
                .genderRequirement(s.genderRequirement())
                .furnitureStatus(s.furnitureStatus())
                .curfewType(s.curfewType())
                .petAllowed(s.petAllowed())
                .parkingAvailable(s.parkingAvailable())
                .categoryCode(s.categoryCode() == null ? null : List.of(s.categoryCode()))
                .build();
    }

    private String buildSearchUrl(ChatSlots s) {
        StringBuilder sb = new StringBuilder("/tim-kiem?");
        List<String> parts = new ArrayList<>();
        if (s.categoryCode() != null) {
            parts.add("categoryCode=" + s.categoryCode().name());
        }
        if (s.priceFrom() != null) {
            parts.add("priceFrom=" + s.priceFrom().toPlainString());
        }
        if (s.priceTo() != null) {
            parts.add("priceTo=" + s.priceTo().toPlainString());
        }
        if (s.areaFrom() != null) {
            parts.add("areaFrom=" + s.areaFrom().toPlainString());
        }
        if (s.maxOccupants() != null) {
            parts.add("maxOccupants=" + s.maxOccupants());
        }
        if (s.locationKeyword() != null) {
            parts.add("keyword=" + java.net.URLEncoder.encode(s.locationKeyword(),
                    java.nio.charset.StandardCharsets.UTF_8));
        }
        return sb.append(String.join("&", parts)).toString();
    }

    private List<ChatbotMessageResponse.ExpansionSuggestion> defaultExpansions() {
        List<ChatbotMessageResponse.ExpansionSuggestion> list = new ArrayList<>();
        list.add(ChatbotMessageResponse.ExpansionSuggestion.builder()
                .type("EXPAND_PRICE").label("Nới khoảng giá").build());
        list.add(ChatbotMessageResponse.ExpansionSuggestion.builder()
                .type("EXPAND_AREA_SCOPE").label("Mở rộng khu vực lân cận").build());
        list.add(ChatbotMessageResponse.ExpansionSuggestion.builder()
                .type("EXPAND_SIZE").label("Chấp nhận diện tích nhỏ hơn").build());
        return list;
    }

    private String describeMissing(List<String> missing) {
        List<String> vi = new ArrayList<>();
        for (String m : missing) {
            switch (m) {
                case "location" -> vi.add("khu vực muốn tìm");
                case "priceTo" -> vi.add("khoảng giá");
                case "maxOccupants" -> vi.add("số người ở");
                default -> { /* bỏ qua */ }
            }
        }
        return String.join(" và ", vi);
    }

    private ChatbotListingItem toItem(ListingSummaryResponse s) {
        return ChatbotListingItem.builder()
                .id(s.getId())
                .slug(s.getSlug())
                .title(s.getTitle())
                .price(s.getPrice())
                .area(s.getArea())
                .shortAddress(s.getShortAddress())
                .thumbnailUrl(s.getThumbnailUrl())
                .averageRating(s.getAverageRating())
                .promoted(s.getPromoted())
                .build();
    }

    private ChatSlots parseSlots(String json) {
        if (json == null || json.isBlank()) {
            return ChatSlots.empty();
        }
        try {
            return objectMapper.readValue(json, ChatSlots.class);
        } catch (Exception e) {
            log.warn("Không parse được slot hội thoại: {}", e.getMessage());
            return ChatSlots.empty();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Không serialize được JSON chatbot: {}", e.getMessage());
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> parseMap(String json) {
        try {
            return objectMapper.readValue(json, java.util.Map.class);
        } catch (Exception e) {
            return java.util.Map.of();
        }
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
