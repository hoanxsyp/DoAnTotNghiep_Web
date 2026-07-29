package com.webtro.modules.interaction.dto.request;

import com.webtro.validator.NoHtml;
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

/**
 * Yêu cầu tạo/sửa đánh giá — {@code POST /api/listings/{id}/reviews} + {@code PUT /api/reviews/{id}}
 * (canonical 4.7.8, 4.7.9). Rating 1–5; {@code content} bắt buộc khi {@code rating <= 2}
 * (kiểm tra ở service — phụ thuộc field khác) {@code [§3.12]}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CreateReviewRequest", description = "Yêu cầu tạo/sửa đánh giá")
public class CreateReviewRequest {

    @Schema(description = "Số sao (1–5)", example = "5")
    @NotNull(message = "Vui lòng chọn số sao")
    @Min(value = 1, message = "Số sao phải từ 1 đến 5")
    @Max(value = 5, message = "Số sao phải từ 1 đến 5")
    private Integer rating;

    @Schema(description = "Nội dung đánh giá (bắt buộc khi chấm từ 2 sao trở xuống)",
            example = "Chủ trọ dễ tính, phòng sạch sẽ, đúng như mô tả. Rất đáng tiền.")
    @Size(min = 10, max = 2000, message = "Nội dung đánh giá phải từ 10 đến 2000 ký tự")
    @NoHtml
    private String content;
}
