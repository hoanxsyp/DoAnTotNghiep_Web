package com.webtro.modules.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Kết quả đánh dấu đã đọc một thông báo (docs/03 mục 4.10.3).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "MarkReadResponse", description = "Kết quả đánh dấu đã đọc")
public class MarkReadResponse {

    @Schema(description = "Id thông báo", example = "88201")
    private Long id;

    @Schema(description = "Đã đọc", example = "true")
    private boolean read;

    @Schema(description = "Thời điểm đọc (giữ nguyên lần đầu nếu đã đọc trước đó)")
    private Instant readAt;

    @Schema(description = "Số thông báo chưa đọc còn lại", example = "4")
    private long unreadCount;
}
