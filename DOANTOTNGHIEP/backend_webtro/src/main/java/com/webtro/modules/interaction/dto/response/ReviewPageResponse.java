package com.webtro.modules.interaction.dto.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.webtro.common.PageResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * Trang đánh giá kèm thống kê tổng (điểm trung bình + phân bố sao) — canonical 4.7.6, 4.7.12.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ReviewPageResponse", description = "Trang đánh giá + thống kê")
public class ReviewPageResponse {

    @JsonUnwrapped
    private PageResponse<ReviewResponse> page;

    private ReviewSummary summary;

    /** Thống kê đánh giá: trung bình, tổng, phân bố theo số sao. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "ReviewSummary", description = "Thống kê đánh giá")
    public static class ReviewSummary {
        private java.math.BigDecimal averageRating;
        private long totalReviews;

        @Schema(description = "Phân bố số lượng theo số sao, khóa \"1\"..\"5\"")
        private Map<String, Long> distribution;
    }
}
