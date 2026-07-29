package com.webtro.modules.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Số thông báo và tin nhắn chưa đọc — phục vụ badge, FE poll 30 giây (docs/03 mục 4.10.2).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "UnreadCountResponse", description = "Số chưa đọc phục vụ badge")
public class UnreadCountResponse {

    @Schema(description = "Số thông báo chưa đọc", example = "5")
    private long unreadCount;

    @Schema(description = "Số tin nhắn chat chưa đọc (gộp để FE chỉ poll một lần)", example = "3")
    private long unreadMessageCount;
}
