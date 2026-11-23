package com.webtro.modules.interaction.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Kết quả kiểm tra điều kiện đánh giá — {@code GET /api/listings/{id}/reviews/eligibility}
 * (canonical 4.7.7). Luôn trả 200.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ReviewEligibilityResponse", description = "Điều kiện đánh giá tin")
public class ReviewEligibilityResponse {

    private Long listingId;
    /** Đánh giá áp dụng cho cả phòng, không riêng tin (RE-1). */
    private Long rentalUnitId;
    private String rentalUnitName;
    private Boolean eligible;

    @Schema(description = "Lý do không đủ điều kiện: NOT_RENTER/ALREADY_REVIEWED/OWN_LISTING/NOT_AUTHENTICATED")
    private String reason;

    private String reasonMessage;
    private Boolean alreadyReviewed;
    private Long existingReviewId;
    @Schema(description = "Field tương thích ngược; luôn false vì liên hệ không còn là điều kiện đánh giá", deprecated = true)
    private Boolean contactRequired;

    @Schema(description = "Field tương thích ngược; phản ánh rentalVerified", deprecated = true)
    private Boolean hasContacted;

    @Schema(description = "True khi người dùng đang thuê hoặc đã trả phòng của tin này")
    private Boolean rentalVerified;

    @Schema(description = "Ngưỡng số sao mà dưới nó nội dung bắt buộc", example = "3")
    private Integer contentRequiredWhenRatingBelow;
}
