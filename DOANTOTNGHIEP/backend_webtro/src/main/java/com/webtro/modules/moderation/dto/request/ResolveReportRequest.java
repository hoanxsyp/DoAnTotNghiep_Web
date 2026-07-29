package com.webtro.modules.moderation.dto.request;

import com.webtro.common.enums.ModerationResult;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu xử lý (kết luận) một báo cáo (canonical 4.16.4).
 *
 * <p>Ràng buộc liên trường "bắt buộc {@link #warningMessage} khi {@link #result} là
 * MINOR_WARN/MEDIUM_HIDE/SEVERE_LOCK" ép ở service (mã lỗi {@code WARNING_REASON_REQUIRED}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ResolveReportRequest", description = "Kết luận xử lý báo cáo")
public class ResolveReportRequest {

    @NotNull(message = "Vui lòng chọn kết quả xử lý")
    @Schema(description = "Kết quả xử lý", example = "SEVERE_LOCK")
    private ModerationResult result;

    @Size(max = 500, message = "Phản hồi tối đa 500 ký tự")
    @Schema(description = "Phản hồi gửi người báo cáo")
    private String moderatorResponse;

    @Size(max = 500, message = "Nội dung cảnh báo tối đa 500 ký tự")
    @Schema(description = "Cảnh báo gửi người bị báo cáo (bắt buộc khi có vi phạm)")
    private String warningMessage;

    @Size(max = 500, message = "Ghi chú nội bộ tối đa 500 ký tự")
    @Schema(description = "Ghi chú nội bộ (không lộ ra ngoài Moderator/Admin)")
    private String internalNote;

    @Schema(description = "Xử lý luôn mọi báo cáo cùng đối tượng", example = "true")
    @Builder.Default
    private Boolean resolveRelated = true;
}
