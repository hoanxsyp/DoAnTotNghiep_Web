package com.webtro.modules.interaction.mapper;

import com.webtro.modules.interaction.dto.response.ConversationResponse;
import com.webtro.modules.interaction.dto.response.MessageResponse;
import com.webtro.modules.interaction.entity.Conversation;
import com.webtro.modules.interaction.entity.Message;
import com.webtro.modules.interaction.spi.ListingGateway.ListingBrief;
import com.webtro.modules.interaction.spi.UserGateway.UserBrief;
import org.springframework.stereotype.Component;

/**
 * Ánh xạ Conversation/Message → DTO (thủ công, Builder). Vai trò và cờ "của tôi" tính theo
 * {@code currentUserId} truyền vào.
 */
@Component
public class ConversationMapper {

    public ConversationResponse toResponse(Conversation c, Long currentUserId, Long landlordId,
                                           ListingBrief listing, UserBrief partner) {
        boolean iAmLandlord = landlordId != null && landlordId.equals(currentUserId);
        int unread = iAmLandlord ? c.getLandlordUnreadCount() : c.getTenantUnreadCount();

        ConversationResponse.PartnerResponse partnerDto = partner == null ? null
                : ConversationResponse.PartnerResponse.builder()
                .id(partner.id())
                .fullName(partner.fullName())
                .avatarUrl(partner.avatarUrl())
                .online(false)
                .build();

        ConversationResponse.LastMessageResponse lastMsg = c.getLastMessagePreview() == null ? null
                : ConversationResponse.LastMessageResponse.builder()
                .content(c.getLastMessagePreview())
                .sentAt(c.getLastMessageAt())
                .build();

        return ConversationResponse.builder()
                .id(c.getId())
                .listingId(c.getListingId())
                .listingTitle(listing == null ? null : listing.title())
                .listingThumbnailUrl(listing == null ? null : listing.thumbnailUrl())
                .listingPrice(listing == null ? null : listing.price())
                .listingStatus(listing == null || listing.status() == null ? null : listing.status().name())
                .myRole(iAmLandlord ? "LANDLORD" : "TENANT")
                .partner(partnerDto)
                .lastMessage(lastMsg)
                .unreadCount(unread)
                .createdAt(c.getCreatedAt())
                .lastMessageAt(c.getLastMessageAt())
                .build();
    }

    public MessageResponse toMessageResponse(Message m, Long currentUserId, UserBrief sender) {
        return MessageResponse.builder()
                .id(m.getId())
                .conversationId(m.getConversation().getId())
                .senderId(m.getSenderId())
                .senderName(sender == null ? null : sender.fullName())
                .senderAvatarUrl(sender == null ? null : sender.avatarUrl())
                .sentByMe(m.getSenderId().equals(currentUserId))
                .content(m.getContent())
                .readAt(m.getReadAt())
                .sentAt(m.getCreatedAt())
                .build();
    }
}
