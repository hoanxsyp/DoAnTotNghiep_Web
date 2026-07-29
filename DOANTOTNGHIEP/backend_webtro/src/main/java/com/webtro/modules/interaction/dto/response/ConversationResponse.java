package com.webtro.modules.interaction.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Một cuộc trò chuyện trong danh sách — {@code GET /api/conversations} (canonical 4.6.4).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ConversationResponse", description = "Một cuộc trò chuyện")
public class ConversationResponse {

    private Long id;
    private Long listingId;
    private String listingTitle;
    private String listingThumbnailUrl;
    private BigDecimal listingPrice;
    private String listingStatus;

    @Schema(description = "Vai trò của tôi trong hội thoại (TENANT/LANDLORD)")
    private String myRole;

    private PartnerResponse partner;
    private LastMessageResponse lastMessage;
    private Integer unreadCount;
    private Instant createdAt;
    private Instant lastMessageAt;

    /** Người còn lại trong hội thoại. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "PartnerResponse", description = "Đối phương trong hội thoại")
    public static class PartnerResponse {
        private Long id;
        private String fullName;
        private String avatarUrl;
        @Schema(description = "Luôn false — chat không realtime (canonical 4.6.4)")
        private Boolean online;
    }

    /** Tin nhắn gần nhất (xem nhanh). */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "LastMessageResponse", description = "Tin nhắn gần nhất")
    public static class LastMessageResponse {
        private String content;
        private Long senderId;
        private Boolean sentByMe;
        private Instant sentAt;
    }
}
