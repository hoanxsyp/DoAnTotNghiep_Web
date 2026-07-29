package com.webtro.modules.interaction.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Một đánh giá nhìn từ màn hình kiểm duyệt ({@code GET /api/admin/reviews} và các thao tác
 * ẩn/khôi phục) — quyền {@code REVIEW_MODERATE}.
 *
 * <p>Khác {@link ReviewResponse} công khai: bổ sung thông tin kiểm duyệt ({@code hidden*}) và các
 * khóa liên quan ({@code userId}, {@code landlordId}) để kiểm duyệt viên tra cứu.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AdminReviewResponse", description = "Đánh giá (góc nhìn kiểm duyệt)")
public class AdminReviewResponse {

    private Long id;
    private Long listingId;
    private Long landlordId;
    private Integer rating;
    private String content;

    @Schema(description = "Trạng thái (VISIBLE/HIDDEN/DELETED)")
    private String status;

    @Schema(description = "Nhãn trạng thái tiếng Việt")
    private String statusLabel;

    @Schema(description = "Tác giả đánh giá")
    private AuthorResponse author;

    @Schema(description = "Người đánh giá đã từng liên hệ tin")
    private Boolean isVerifiedContact;

    @Schema(description = "Lý do bị ẩn (chỉ có khi đã ẩn)")
    private String hiddenReason;

    @Schema(description = "Người ẩn (users.id)")
    private Long hiddenBy;

    @Schema(description = "Thời điểm bị ẩn")
    private Instant hiddenAt;

    private Instant editedAt;
    private Instant createdAt;
}
