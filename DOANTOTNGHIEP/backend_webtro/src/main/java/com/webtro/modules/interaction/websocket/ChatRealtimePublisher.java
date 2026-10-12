package com.webtro.modules.interaction.websocket;

import com.webtro.modules.interaction.dto.response.ConversationResponse;
import com.webtro.modules.interaction.dto.response.MessageResponse;

public interface ChatRealtimePublisher {

    /** {@code MESSAGE_CREATED} tới các kết nối đang mở (SUBSCRIBE) đúng hội thoại này. */
    void publishMessage(Long conversationId, MessageResponse message);

    /**
     * {@code CONVERSATION_UPDATED} tới MỌI kết nối của một người dùng (dù đang ở trang nào): hội
     * thoại theo góc nhìn của người đó + tổng số tin chưa đọc của họ.
     */
    void publishConversationUpdate(Long userId, ConversationResponse conversation, long totalUnread);
}
