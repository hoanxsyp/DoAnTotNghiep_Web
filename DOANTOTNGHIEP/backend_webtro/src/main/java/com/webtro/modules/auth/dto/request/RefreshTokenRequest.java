package com.webtro.modules.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu làm mới token (AUTH — refresh) — {@code POST /api/auth/refresh}.
 *
 * <p>Refresh token được ưu tiên đọc từ cookie httpOnly {@code refresh_token} (canonical mục 8).
 * Trường {@code refreshToken} trong body chỉ là phương án dự phòng nên KHÔNG bắt buộc — service
 * kiểm tra sự hiện diện của token (cookie hoặc body) và trả lỗi phù hợp nếu thiếu.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "RefreshTokenRequest", description = "Refresh token (dự phòng khi không dùng cookie)")
public class RefreshTokenRequest {

    @Schema(description = "Refresh token opaque (UUID) — có thể bỏ trống nếu đã gửi qua cookie", nullable = true)
    private String refreshToken;
}
