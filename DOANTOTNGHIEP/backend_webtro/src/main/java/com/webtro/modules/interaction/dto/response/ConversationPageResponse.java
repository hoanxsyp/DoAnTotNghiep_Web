package com.webtro.modules.interaction.dto.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.webtro.common.PageResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Trang danh sách hội thoại kèm tổng số hội thoại chưa đọc (canonical 4.6.4).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ConversationPageResponse", description = "Trang hội thoại + tổng chưa đọc")
public class ConversationPageResponse {

    @JsonUnwrapped
    private PageResponse<ConversationResponse> page;

    @Schema(description = "Tổng số hội thoại còn tin chưa đọc")
    private long totalUnreadConversations;
}
