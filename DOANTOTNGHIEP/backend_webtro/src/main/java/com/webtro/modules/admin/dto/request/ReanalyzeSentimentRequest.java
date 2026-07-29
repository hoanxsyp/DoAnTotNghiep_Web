package com.webtro.modules.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu phân tích lại cảm xúc cho một bình luận có sẵn từ màn quản trị AI (canonical 4.19, AI-07).
 * Luôn ghi đè {@code sentiment_results} + tính lại điểm uy tín (persist), khác với endpoint chẩn đoán
 * thử nghiệm văn bản.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ReanalyzeSentimentRequest", description = "Yêu cầu phân tích lại cảm xúc một bình luận")
public class ReanalyzeSentimentRequest {

    @NotNull(message = "Vui lòng cung cấp id bình luận cần phân tích lại")
    @Schema(description = "Id bình luận cần phân tích lại", example = "4501")
    private Long commentId;

    @Schema(description = "Id tin đăng của bình luận (tùy chọn, chỉ để đối chiếu ở FE)", example = "1024")
    private Long listingId;
}
