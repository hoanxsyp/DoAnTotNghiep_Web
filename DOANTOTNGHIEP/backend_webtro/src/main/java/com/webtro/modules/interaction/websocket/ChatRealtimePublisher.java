package com.webtro.modules.interaction.websocket;

import com.webtro.modules.interaction.dto.response.MessageResponse;

public interface ChatRealtimePublisher {

    void publishMessage(Long conversationId, MessageResponse message);
}
