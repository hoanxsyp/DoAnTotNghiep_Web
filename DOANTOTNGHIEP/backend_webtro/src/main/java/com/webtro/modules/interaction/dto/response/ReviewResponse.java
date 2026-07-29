package com.webtro.modules.interaction.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Một đánh giá — {@code GET/POST /api/listings/{id}/reviews}, {@code PUT /api/reviews/{id}},
 * {@code GET /api/reviews/my}, {@code GET /api/users/{id}/reviews} (canonical 4.7.6–4.7.12).
 *
 * <p>Các trường {@code listing*}, {@code landlordAverageRating}, {@code moderationReason} chỉ có
 * mặt ở một số endpoint (null sẽ bị loại khỏi JSON).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ReviewResponse", description = "Một đánh giá")
public class ReviewResponse {

    private Long id;
    private Long listingId;
    private Integer rating;
    private String content;
    private String status;
    private AuthorResponse author;

    @Schema(description = "Người xem hiện tại có sửa được không (tác giả + trong cửa sổ)")
    private Boolean editable;

    @Schema(description = "Thời điểm hết hạn sửa")
    private Instant editableUntil;

    private Instant editedAt;
    private Instant createdAt;

    // ----- Chỉ có ở một số endpoint -----

    private String listingTitle;
    private String listingThumbnailUrl;
    private String listingStatus;

    @Schema(description = "Điểm trung bình mới của tin (sau khi tạo/sửa)")
    private BigDecimal listingAverageRating;

    @Schema(description = "Số đánh giá mới của tin")
    private Integer listingReviewCount;

    @Schema(description = "Điểm trung bình mới của chủ trọ")
    private BigDecimal landlordAverageRating;

    @Schema(description = "Lý do bị ẩn (chỉ hiện cho tác giả ở /api/reviews/my)")
    private String moderationReason;
}
