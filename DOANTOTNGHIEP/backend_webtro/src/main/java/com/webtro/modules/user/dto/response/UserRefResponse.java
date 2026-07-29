package com.webtro.modules.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tham chiếu người dùng rút gọn (id + tên + avatar) — dùng cho các module khác (listing,
 * interaction) nhúng thông tin chủ trọ/tác giả mà KHÔNG cần chạm entity {@code User}
 * (canonical luật 4). Trả qua {@code UserService.loadUserRef}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "UserRefResponse", description = "Tham chiếu người dùng rút gọn")
public class UserRefResponse {

    @Schema(description = "Id người dùng", example = "42")
    private Long id;

    @Schema(description = "Họ tên", example = "Nguyễn Văn An")
    private String fullName;

    @Schema(description = "URL ảnh đại diện (null nếu chưa có)")
    private String avatarUrl;
}
