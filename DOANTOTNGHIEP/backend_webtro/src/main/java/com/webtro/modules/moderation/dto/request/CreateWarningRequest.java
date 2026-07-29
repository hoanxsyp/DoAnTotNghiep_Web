package com.webtro.modules.moderation.dto.request;

import com.webtro.common.enums.ReportSeverity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu gửi cảnh báo vi phạm trực tiếp cho một người dùng (canonical 4.16.5, {@code [§2.8]}
 * RPT-05, {@code [§5.4]}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CreateWarningRequest", description = "Gửi cảnh báo vi phạm")
public class CreateWarningRequest {

    @NotNull(message = "Thiếu người nhận cảnh báo")
    @Positive(message = "Định danh người dùng không hợp lệ")
    @Schema(description = "Người nhận cảnh báo (users.id)", example = "117")
    private Long userId;

    @NotBlank(message = "Vui lòng nhập nội dung cảnh báo")
    @Size(min = 10, max = 500, message = "Nội dung cảnh báo từ 10 đến 500 ký tự")
    @Schema(description = "Nội dung cảnh báo gửi người dùng")
    private String reason;

    @NotNull(message = "Vui lòng chọn mức độ")
    @Schema(description = "Mức độ nghiêm trọng", example = "MEDIUM")
    private ReportSeverity severity;

    @Positive(message = "Định danh tin không hợp lệ")
    @Schema(description = "Tin liên quan (thuộc userId) — tùy chọn", example = "877")
    private Long relatedListingId;

    @Positive(message = "Định danh báo cáo không hợp lệ")
    @Schema(description = "Báo cáo liên quan — tùy chọn", example = "6601")
    private Long relatedReportId;

    @Schema(description = "Gửi email cảnh báo", example = "true")
    @Builder.Default
    private Boolean notifyByEmail = true;
}
