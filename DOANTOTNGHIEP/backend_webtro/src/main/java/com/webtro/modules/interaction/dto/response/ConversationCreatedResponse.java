package com.webtro.modules.interaction.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Kết quả tạo cuộc trò chuyện — {@code POST /api/conversations} (canonical 4.6.5).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ConversationCreatedResponse", description = "Kết quả tạo cuộc trò chuyện")
public class ConversationCreatedResponse {

    private Long id;
    private Long listingId;
    private String listingTitle;
    private Long tenantId;
    private Long landlordId;
    private String myRole;

    @Schema(description = "Hội thoại đã tồn tại từ trước (trả lại thay vì tạo mới)")
    private Boolean alreadyExisted;

    private Long firstMessageId;
    private Instant createdAt;
}
