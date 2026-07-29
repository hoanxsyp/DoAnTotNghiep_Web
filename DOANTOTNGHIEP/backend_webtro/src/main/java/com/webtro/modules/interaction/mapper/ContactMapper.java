package com.webtro.modules.interaction.mapper;

import com.webtro.modules.interaction.dto.response.LandlordContactResponse;
import com.webtro.modules.interaction.entity.ContactLog;
import com.webtro.modules.interaction.spi.UserGateway.UserBrief;
import org.springframework.stereotype.Component;

/**
 * Ánh xạ ContactLog → DTO danh sách người liên hệ của chủ trọ (thủ công, Builder).
 */
@Component
public class ContactMapper {

    public LandlordContactResponse toLandlordContact(ContactLog log, String listingTitle,
                                                     Long conversationId, UserBrief tenant) {
        LandlordContactResponse.TenantResponse tenantDto = tenant == null ? null
                : LandlordContactResponse.TenantResponse.builder()
                .id(tenant.id())
                .fullName(tenant.fullName())
                .avatarUrl(tenant.avatarUrl())
                .phone(tenant.phone())
                .memberSince(tenant.memberSince())
                .build();

        return LandlordContactResponse.builder()
                .contactLogId(log.getId())
                .listingId(log.getListingId())
                .listingTitle(listingTitle)
                .type(log.getContactType() == null ? null : log.getContactType().name())
                .typeLabel(log.getContactType() == null ? null : log.getContactType().getLabel())
                .message(log.getMessage())
                .callbackPhone(log.getContactPhone())
                .conversationId(conversationId)
                .isReadByOwner(log.getIsReadByOwner())
                .tenant(tenantDto)
                .createdAt(log.getCreatedAt())
                .build();
    }
}
