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
 * Một tin bị AI đánh dấu cần kiểm tra do tỷ lệ bình luận tiêu cực cao (canonical 4.19, AI-07).
 *
 * <p>Dạng rút gọn cho tab "Cảnh báo cảm xúc" của màn Log &amp; cảnh báo AI: kèm tỷ lệ tiêu cực và
 * số bình luận tiêu cực để Moderator ưu tiên xử lý.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "AiAlertResponse", description = "Tin bị AI cảnh báo cảm xúc tiêu cực")
public class AiAlertResponse {

    @Schema(description = "Id tin đăng", example = "1024")
    private Long listingId;

    @Schema(description = "Tiêu đề tin", example = "Phòng trọ Q.Bình Thạnh")
    private String listingTitle;

    @Schema(description = "Id chủ trọ", example = "42")
    private Long ownerId;

    @Schema(description = "Trạng thái tin hiện tại", example = "ACTIVE")
    private String status;

    @Schema(description = "Nhãn cảm xúc chủ đạo của cảnh báo (luôn NEGATIVE)", example = "NEGATIVE")
    private String sentimentLabel;

    @Schema(description = "Tỷ lệ bình luận tiêu cực (0..1)", example = "0.62")
    private BigDecimal negativeRatio;

    @Schema(description = "Số bình luận tiêu cực", example = "8")
    private Integer negativeCount;

    @Schema(description = "Tổng số bình luận đã tính cảm xúc", example = "13")
    private Integer totalComments;

    @Schema(description = "Số lần bị đánh dấu cần kiểm tra", example = "2")
    private Integer needReviewCount;

    @Schema(description = "Điểm uy tín hiện tại của tin", example = "68")
    private Integer trustScore;

    @Schema(description = "Thời điểm bị đánh dấu cần kiểm tra gần nhất")
    private Instant flaggedAt;

    @Schema(description = "Alias của flaggedAt để tương thích cột 'Phát hiện' ở FE")
    private Instant createdAt;
}
