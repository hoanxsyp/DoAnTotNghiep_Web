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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Hiện thực nghiệp vụ chatbot (canonical mục 10.3, §9.3, §3.15). Ràng buộc cứng: chỉ trả tin công
 * khai (qua {@code ListingSearchService}), không bịa thông tin, luôn kèm disclaimer khi trả tin,
 * hỏi lại tối đa {@code ai.chatbot.max_clarify_turns}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatbotServiceImpl implements ChatbotService {

    private static final int RESULT_LIMIT = 5;
    private static final String DISCLAIMER =
            "Thông tin lấy trực tiếp từ tin đăng. Bạn vui lòng liên hệ chủ trọ để xác nhận phòng còn trống.";

    private final ChatbotEngine engine;
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

        // Xử lý theo intent.
        List<ChatbotListingItem> listings = new ArrayList<>();
        int totalResults = 0;
        String reply;
        List<String> missing = result.missingImportantSlots();
        String searchUrl = null;
        List<ChatbotMessageResponse.ExpansionSuggestion> expansions = null;
        boolean fallback = false;

        if (result.intent() == ChatbotIntent.FIND_ROOM) {
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
        } else if (result.intent() == ChatbotIntent.GLOSSARY) {
            reply = result.cannedReply();
        } else {
            reply = result.cannedReply();
            fallback = result.intent() == ChatbotIntent.UNKNOWN || result.intent() == ChatbotIntent.OUT_OF_SCOPE;
        }

        // Lưu hội thoại + hai tin nhắn.
        String mergedSlotsJson = toJson(result.slots());
        conv.setLastIntent(result.intent());
        conv.setCollectedFilters(mergedSlotsJson);
        conv.setLastMessageAt(Instant.now());
        conv.setMessageCount(conv.getMessageCount() + 2);
        conversationRepository.save(conv);

        // Tin của USER: mang intent (BOT phải null theo ck_chatbot_messages_bot_intent).
        ChatbotMessage userMsg = ChatbotMessage.builder()
                .conversation(conv)
                .sender(ChatbotSender.USER)
                .content(sanitized)
                .intent(result.intent())
                .intentConfidence(result.confidence())
                .extractedSlots(mergedSlotsJson)
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
                .extractedSlots(mergedSlotsJson)
                .resultListingIds(toJson(listingIds))
                .resultCount(totalResults)
                .isFallback(fallback)
                .responseMs(responseMs)
                .build();
        messageRepository.save(botMsg);

        return ChatbotMessageResponse.builder()
                .conversationId(conv.getId())
                .messageId(botMsg.getId())
                .intent(result.intent())
                .intentConfidence(result.confidence())
                .reply(reply)
                .extractedSlots(parseMap(mergedSlotsJson))
                .missingSlots(missing)
                .clarifyTurn(conv.getClarifyTurnCount())
                .maxClarifyTurns(maxClarify)
                .listings(listings)
                .totalResults(totalResults)
                .searchUrl(searchUrl)
                .expansionSuggestions(expansions)
                .glossaryTerm(result.glossaryTerm())
                .disclaimer(listings.isEmpty() ? null : DISCLAIMER)
                .flaggedForReview(result.sensitive())
                .createdAt(botMsg.getCreatedAt())
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
                .categoryCode(s.categoryCode())
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
                .trustScore(s.getTrustScore())
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
