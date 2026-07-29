package com.webtro.modules.user.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.webtro.common.enums.TrustLabel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Một mục trong danh sách chủ trọ đang theo dõi —
 * {@code GET /api/users/me/following} (FOLLOW-02, docs/03 mục 4.2.9).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "FollowingItemResponse", description = "Chủ trọ đang theo dõi")
public class FollowingItemResponse {

    @Schema(description = "Id chủ trọ", example = "42")
    private Long landlordId;

    @Schema(description = "Họ tên chủ trọ", example = "Nguyễn Văn An")
    private String fullName;

    @Schema(description = "URL ảnh đại diện")
    private String avatarUrl;

    @Schema(description = "Đã được xác minh chưa", example = "true")
    private boolean verified;

    @Schema(description = "Điểm uy tín", example = "87")
    private Integer trustScore;

    @Schema(description = "Nhãn uy tín", example = "GOOD")
    private TrustLabel trustLabel;

    @Schema(description = "Điểm đánh giá trung bình", example = "4.5")
    private BigDecimal averageRating;

    @Schema(description = "Số tin đang hoạt động", example = "4")
    private Integer activeListingCount;

    @Schema(description = "Thời điểm bắt đầu theo dõi")
    private Instant followedAt;
}
