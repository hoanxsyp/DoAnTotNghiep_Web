package com.webtro.modules.interaction.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Một tin nhắn — {@code GET/POST /api/conversations/{id}/messages} (canonical 4.6.7, 4.6.8).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "MessageResponse", description = "Một tin nhắn")
public class MessageResponse {

    private Long id;
    private Long conversationId;
    private Long senderId;
    private String senderName;
    private String senderAvatarUrl;

    @Schema(description = "Do chính tôi gửi")
    private Boolean sentByMe;

    private String content;
    private Instant readAt;
    private Instant sentAt;
}
