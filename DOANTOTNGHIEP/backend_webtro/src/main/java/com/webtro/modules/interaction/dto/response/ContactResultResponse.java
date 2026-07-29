package com.webtro.modules.interaction.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Kết quả gửi yêu cầu liên hệ — {@code POST /api/listings/{id}/contact} (canonical 4.6.2).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ContactResultResponse", description = "Kết quả gửi yêu cầu liên hệ")
public class ContactResultResponse {

    private Long contactLogId;
    private Long listingId;

    @Schema(description = "Hình thức liên hệ (VIEW_PHONE/SEND_FORM/START_CHAT)")
    private String type;

    @Schema(description = "Id hội thoại nếu là START_CHAT")
    private Long conversationId;

    @Schema(description = "Số điện thoại chủ trọ (chỉ trả khi type = VIEW_PHONE)")
    private String contactPhone;

    @Schema(description = "Tổng số lượt liên hệ của tin sau thao tác")
    private Integer contactCount;

    @Schema(description = "Lượt liên hệ này bị khử trùng (không tăng contactCount)")
    private Boolean deduplicated;

    @Schema(description = "Đã thông báo cho chủ trọ chưa")
    private Boolean landlordNotified;

    private Instant createdAt;
}
