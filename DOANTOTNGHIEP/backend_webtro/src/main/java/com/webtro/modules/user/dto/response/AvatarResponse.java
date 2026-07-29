package com.webtro.modules.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Kết quả cập nhật ảnh đại diện — {@code POST /api/users/me/avatar} (docs/03 mục 4.2.4).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AvatarResponse", description = "URL ảnh đại diện sau khi cập nhật")
public class AvatarResponse {

    @Schema(description = "URL ảnh đại diện (bản đầy đủ)")
    private String avatarUrl;

    @Schema(description = "URL ảnh thu nhỏ (thumbnail)")
    private String thumbnailUrl;
}
