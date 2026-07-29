package com.webtro.modules.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu khóa tài khoản (canonical 4.13.3). {@code reason} kiểm tra rỗng ở service để trả đúng
 * mã {@code LOCK_REASON_REQUIRED} (thay vì {@code VALIDATION_FAILED}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "LockUserRequest", description = "Yêu cầu khóa tài khoản")
public class LockUserRequest {

    @Schema(description = "Lý do khóa (10–500 ký tự)")
    @Size(min = 10, max = 500, message = "Lý do phải từ 10 đến 500 ký tự")
    private String reason;

    @Schema(description = "Gửi thông báo cho người dùng", example = "true")
    @Builder.Default
    private Boolean notifyUser = true;

    @Schema(description = "Khóa luôn toàn bộ tin đang hiển thị của user", example = "false")
    @Builder.Default
    private Boolean lockListings = false;
}
