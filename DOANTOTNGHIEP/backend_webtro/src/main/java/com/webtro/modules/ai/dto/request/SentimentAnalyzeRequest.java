package com.webtro.modules.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu phân tích lại cảm xúc (endpoint chẩn đoán của Admin/Moderator, docs/03 mục 7.1).
 * Đúng <b>một</b> trong hai: {@code commentId} (phân tích lại bình luận có sẵn) hoặc {@code text}
 * (thử nghiệm từ điển). Ràng buộc "đúng một trong hai" kiểm ở tầng service.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "SentimentAnalyzeRequest", description = "Yêu cầu phân tích cảm xúc (chẩn đoán)")
public class SentimentAnalyzeRequest {

    @Schema(description = "Phân tích lại bình luận có sẵn theo id", example = "4501")
    private Long commentId;

    @Size(min = 1, max = 1000, message = "Văn bản thử nghiệm phải từ 1 đến 1000 ký tự")
    @Schema(description = "Văn bản bất kỳ để thử nghiệm từ điển (thay cho commentId)")
    private String text;

    @Min(value = 0, message = "Tuổi tài khoản không được âm")
    @Schema(description = "Mô phỏng trọng số tài khoản mới (chỉ dùng với text)", example = "3")
    private Integer accountAgeDays;

    @Schema(description = "true = ghi đè sentiment_results + tính lại điểm uy tín (chỉ với commentId)",
            example = "false")
    private Boolean persist;
}
