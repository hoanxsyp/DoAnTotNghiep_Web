package com.webtro.modules.interaction.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tác giả rút gọn của bình luận/đánh giá (canonical 4.7.1, 4.7.6).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AuthorResponse", description = "Thông tin rút gọn của tác giả")
public class AuthorResponse {

    private Long id;
    private String fullName;
    private String avatarUrl;

    @Schema(description = "Tác giả có phải chủ tin đang xem không (chỉ dùng cho bình luận)")
    private Boolean isLandlordOfListing;
}
