package com.webtro.modules.ai.service;

import com.webtro.common.PageResponse;
import com.webtro.modules.ai.dto.request.ChatbotMessageRequest;
import com.webtro.modules.ai.dto.response.ChatbotConversationResponse;
import com.webtro.modules.ai.dto.response.ChatbotMessageHistoryResponse;
import com.webtro.modules.ai.dto.response.ChatbotMessageResponse;
import com.webtro.security.CustomUserDetails;
import org.springframework.data.domain.Pageable;

/**
 * Nghiệp vụ chatbot (AI-05, canonical mục 10.3, §9.3, §3.15).
 */
public interface ChatbotService {

    /**
     * Xử lý một tin nhắn chatbot (công khai). Hiểu intent + slot, tra tin công khai qua
     * {@code ListingSearchService}, hỏi lại tối đa {@code ai.chatbot.max_clarify_turns}, kèm
     * disclaimer khi trả danh sách tin. Lưu {@code ChatbotConversation}/{@code ChatbotMessage}.
     */
    ChatbotMessageResponse handleMessage(ChatbotMessageRequest request, CustomUserDetails principal);

    /** Danh sách phiên chatbot của người dùng đăng nhập. */
    PageResponse<ChatbotConversationResponse> listConversations(Long userId, Pageable pageable);

    /** Lịch sử tin nhắn của một phiên (chỉ chủ phiên xem được). */
    PageResponse<ChatbotMessageHistoryResponse> listMessages(Long conversationId, Long userId,
                                                             Pageable pageable);
}
