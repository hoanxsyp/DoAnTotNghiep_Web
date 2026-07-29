package com.webtro.modules.notification.dto.response;

import com.webtro.common.enums.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tùy chọn nhận thông báo của một loại (docs/03 mục 4.10.6).
 *
 * <p>{@code optional = false} nghĩa là loại bắt buộc — FE hiển thị khóa, không cho tắt.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "NotificationPreferenceResponse", description = "Tùy chọn nhận thông báo một loại")
public class NotificationPreferenceResponse {

    @Schema(description = "Loại thông báo", example = "LISTING_EXPIRING")
    private NotificationType type;

    @Schema(description = "Nhãn tiếng Việt của loại", example = "Tin sắp hết hạn")
    private String typeLabel;

    @Schema(description = "Bật nhận trong ứng dụng", example = "true")
    private boolean inApp;

    @Schema(description = "Bật nhận qua email", example = "true")
    private boolean email;

    @Schema(description = "Có được phép tắt không (false = loại bắt buộc, khóa)", example = "true")
    private boolean optional;
}
