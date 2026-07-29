package com.webtro.modules.ai.dto.request;

import com.webtro.common.enums.RecommendationSource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Yêu cầu gợi ý tin đăng (docs/03 mục 7.2, §9.2). {@code listingId} bắt buộc khi
 * {@code source ∈ {SIMILAR_LISTING, AFTER_FAVORITE}} — kiểm ở service.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "RecommendationRequest", description = "Yêu cầu gợi ý tin đăng")
public class RecommendationRequest {

    @NotNull(message = "Vui lòng chọn ngữ cảnh gợi ý")
    @Schema(description = "Ngữ cảnh gợi ý", example = "HOMEPAGE")
    private RecommendationSource source;

    @Schema(description = "Tin làm gốc (bắt buộc khi source = SIMILAR_LISTING/AFTER_FAVORITE)", example = "1024")
    private Long listingId;

    @Min(value = 1, message = "Số tin tối thiểu là 1")
    @Max(value = 24, message = "Số tin tối đa là 24")
    @Schema(description = "Số tin muốn lấy (mặc định ai.recommendation.size = 12)", example = "12")
    private Integer size;

    @Schema(description = "Cold start: gợi ý theo tỉnh đang chọn", example = "79")
    private Long provinceId;

    @Schema(description = "Cold start: gợi ý theo quận đang chọn", example = "765")
    private Long districtId;

    @Size(max = 50, message = "Tối đa 50 tin loại trừ")
    @Schema(description = "Tin FE đã hiển thị (chống lặp)")
    private List<Long> excludeListingIds;
}
