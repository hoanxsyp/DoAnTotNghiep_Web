package com.webtro.modules.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Kết quả đánh dấu đã đọc tất cả (hoặc theo một loại) — docs/03 mục 4.10.4.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "MarkAllReadResponse", description = "Kết quả đánh dấu đã đọc hàng loạt")
public class MarkAllReadResponse {

    @Schema(description = "Số thông báo vừa được đánh dấu", example = "5")
    private long markedCount;

    @Schema(description = "Số thông báo chưa đọc còn lại", example = "0")
    private long unreadCount;
}
