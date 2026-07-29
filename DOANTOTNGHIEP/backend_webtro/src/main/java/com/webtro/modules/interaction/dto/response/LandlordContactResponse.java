package com.webtro.modules.interaction.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Một người liên hệ mà chủ trọ nhận được — {@code GET /api/landlord/contacts} (canonical 4.6.3).
 * SĐT người thuê hiển thị đầy đủ (chia sẻ có chủ đích {@code [§3.10]}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "LandlordContactResponse", description = "Một người liên hệ tin của chủ trọ")
public class LandlordContactResponse {

    private Long contactLogId;
    private Long listingId;
    private String listingTitle;
    private String type;
    private String typeLabel;
    private String message;
    private String callbackPhone;
    private Long conversationId;
    private Boolean isReadByOwner;
    private TenantResponse tenant;
    private Instant createdAt;

    /** Thông tin người thuê đã liên hệ. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "TenantResponse", description = "Người thuê đã liên hệ")
    public static class TenantResponse {
        private Long id;
        private String fullName;
        private String avatarUrl;
        private String phone;
        private Instant memberSince;
    }
}
