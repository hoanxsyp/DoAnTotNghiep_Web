package com.webtro.modules.interaction.service;

import com.webtro.common.PageResponse;
import com.webtro.modules.interaction.dto.request.CreateConversationRequest;
import com.webtro.modules.interaction.dto.request.SendMessageRequest;
import com.webtro.modules.interaction.dto.response.ConversationCreatedResponse;
import com.webtro.modules.interaction.dto.response.ConversationPageResponse;
import com.webtro.modules.interaction.dto.response.ConversationResponse;
import com.webtro.modules.interaction.dto.response.MessageResponse;
import org.springframework.data.domain.Pageable;

/**
 * Nghiệp vụ hội thoại và tin nhắn nội bộ — CONT-03, canonical mục 4.6, {@code [§3.10]}.
 */
public interface ConversationService {

    /** Danh sách hội thoại của người dùng theo vai trò/lọc; kèm tổng hội thoại chưa đọc. */
    ConversationPageResponse listConversations(Long userId, String role, boolean unreadOnly,
                                               Long listingId, Pageable pageable);

    /** Tạo (hoặc lấy lại) hội thoại theo bộ ba (tenant, landlord, listing) + gửi tin nhắn đầu. */
    ConversationCreatedResponse createConversation(CreateConversationRequest request, Long userId);

    /** Chi tiết một hội thoại (thành viên mới xem được). */
    ConversationResponse getConversation(Long conversationId, Long userId);

    /** Danh sách tin nhắn của hội thoại (mới → cũ). */
    PageResponse<MessageResponse> listMessages(Long conversationId, Long userId, Pageable pageable);

    /** Gửi tin nhắn (rate limit {@code spam.message.per_minute}); ghi {@code first_response_at} idempotent khi chủ trọ trả lời lần đầu. */
    MessageResponse sendMessage(Long conversationId, SendMessageRequest request, Long userId);

    /** Đánh dấu đã đọc toàn bộ tin nhắn của người kia trong hội thoại. */
    void markRead(Long conversationId, Long userId);

    /**
     * Tổng số tin nhắn chưa đọc của một người dùng trên mọi hội thoại (cả vai trò người thuê và
     * chủ trọ). Phục vụ badge gộp ở {@code GET /api/notifications/unread-count} (docs/03 mục 4.10.2).
     */
    long countUnreadMessages(Long userId);

    /**
     * Tạo/lấy lại hội thoại phục vụ luồng {@code POST /api/listings/{id}/contact} với
     * {@code type = START_CHAT} (CONT-05). Trả về id hội thoại và id tin nhắn đầu.
     */
    ConversationCreatedResponse startChatFromContact(Long listingId, Long tenantId, Long landlordId,
                                                     String initialMessage);
}
