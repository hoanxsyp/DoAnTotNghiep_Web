package com.webtro.modules.interaction.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Thông tin liên hệ của tin — {@code GET /api/listings/{id}/contact-info} (canonical 4.6.1).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ContactInfoResponse", description = "Thông tin liên hệ chủ tin")
public class ContactInfoResponse {

    private Long listingId;
    private String contactName;
    private String contactPhone;
    private String contactZalo;

    @Schema(description = "Số điện thoại đang bị che (luôn false ở endpoint này vì đã đăng nhập)")
    private Boolean phoneMasked;

    @Schema(description = "Chủ trọ có bật chat không")
    private Boolean chatEnabled;

    private Long landlordId;
    private String landlordName;
    private Boolean landlordVerified;

    @Schema(description = "Lượt liên hệ có được ghi nhận lần này không (false nếu trong cửa sổ khử trùng)")
    private Boolean contactLogged;

    @Schema(description = "Id hội thoại nếu đã tồn tại giữa hai bên")
    private Long conversationId;
}
