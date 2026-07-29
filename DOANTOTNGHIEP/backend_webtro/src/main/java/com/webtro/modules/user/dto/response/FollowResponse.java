package com.webtro.modules.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Kết quả theo dõi chủ trọ — {@code POST /api/users/{id}/follow} (FOLLOW-01, docs/03 mục 4.2.7).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "FollowResponse", description = "Kết quả thao tác theo dõi chủ trọ")
public class FollowResponse {

    @Schema(description = "Id chủ trọ", example = "42")
    private Long landlordId;

    @Schema(description = "Đang theo dõi không", example = "true")
    private boolean following;

    @Schema(description = "Số người theo dõi sau thao tác", example = "59")
    private long followerCount;

    @Schema(description = "Thời điểm theo dõi")
    private Instant followedAt;
}
