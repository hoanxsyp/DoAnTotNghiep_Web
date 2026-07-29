package com.webtro.modules.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Toàn bộ tùy chọn nhận thông báo của người dùng (16 loại) — docs/03 mục 4.10.6/4.10.7.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "NotificationPreferencesResponse", description = "Danh sách tùy chọn thông báo")
public class NotificationPreferencesResponse {

    @Schema(description = "Danh sách tùy chọn theo từng loại thông báo")
    private List<NotificationPreferenceResponse> preferences;
}
