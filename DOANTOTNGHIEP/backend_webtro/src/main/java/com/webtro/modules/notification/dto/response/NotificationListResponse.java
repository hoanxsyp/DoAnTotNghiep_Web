package com.webtro.modules.notification.dto.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.webtro.common.PageResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Trang danh sách thông báo kèm tổng số chưa đọc (docs/03 mục 4.10.1).
 *
 * <p>{@link JsonUnwrapped} để các field phân trang ({@code items}, {@code page}, {@code size}...)
 * nằm phẳng cùng cấp với {@code unreadCount} đúng theo mẫu response.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "NotificationListResponse", description = "Trang thông báo + tổng chưa đọc")
public class NotificationListResponse {

    @JsonUnwrapped
    private PageResponse<NotificationResponse> page;

    @Schema(description = "Tổng số thông báo chưa đọc của người dùng", example = "5")
    private long unreadCount;
}
