package com.webtro.modules.notification.dto.request;

import com.webtro.common.enums.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Yêu cầu cập nhật cài đặt thông báo (docs/03 mục 4.10.7).
 *
 * <p>Mỗi phần tử là một loại cần đổi; loại bắt buộc mà bị tắt sẽ bị service từ chối
 * ({@code 422 NOTIFICATION_TYPE_NOT_OPTIONAL}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "UpdatePreferenceRequest", description = "Cập nhật cài đặt thông báo")
public class UpdatePreferenceRequest {

    @Schema(description = "Danh sách cài đặt cần đổi")
    @NotEmpty(message = "Danh sách cài đặt không được rỗng")
    @Size(max = 16, message = "Tối đa 16 loại thông báo")
    @Valid
    private List<PreferenceItem> preferences;

    /** Một dòng cài đặt cho một loại thông báo. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "PreferenceItem", description = "Cài đặt một loại thông báo")
    public static class PreferenceItem {

        @Schema(description = "Loại thông báo", example = "NEW_COMMENT")
        @NotNull(message = "Loại thông báo không được để trống")
        private NotificationType type;

        @Schema(description = "Bật nhận trong ứng dụng", example = "true")
        @NotNull(message = "Trường inApp không được để trống")
        private Boolean inApp;

        @Schema(description = "Bật nhận qua email", example = "false")
        @NotNull(message = "Trường email không được để trống")
        private Boolean email;
    }
}
