package com.webtro.modules.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Một tin bị AI đánh cờ lệch giá ({@code price_deviation_flag = true}) — tab "Tin lệch giá" của màn
 * Log &amp; cảnh báo AI (canonical 4.19, AI-07).
 *
 * <p>Giá đề xuất và tỷ lệ lệch lấy từ bản ghi dự đoán giá gần nhất của tin ({@code prediction_histories}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "AiPriceDeviationResponse", description = "Tin bị AI đánh cờ lệch giá")
public class AiPriceDeviationResponse {

    @Schema(description = "Id tin đăng", example = "1024")
    private Long listingId;

    @Schema(description = "Tiêu đề tin", example = "Phòng trọ Q.Bình Thạnh")
    private String listingTitle;

    @Schema(description = "Id chủ trọ", example = "42")
    private Long ownerId;

    @Schema(description = "Trạng thái tin hiện tại", example = "ACTIVE")
    private String status;

    @Schema(description = "Giá chủ trọ đang niêm yết trên tin", example = "3500000")
    private BigDecimal price;

    @Schema(description = "Giá do AI đề xuất (từ dự đoán gần nhất)", example = "2600000")
    private BigDecimal suggestedPrice;

    @Schema(description = "Giá đầu vào dùng để so lệch (nếu có)", example = "3500000")
    private BigDecimal inputPrice;

    @Schema(description = "Tỷ lệ lệch giữa giá nhập và giá dự đoán", example = "0.35")
    private BigDecimal deviationRatio;

    @Schema(description = "Thời điểm ghi nhận (dự đoán gần nhất hoặc lần cập nhật tin)")
    private Instant createdAt;
}
