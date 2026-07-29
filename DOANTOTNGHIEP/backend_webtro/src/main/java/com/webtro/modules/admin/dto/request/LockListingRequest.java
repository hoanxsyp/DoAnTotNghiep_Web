package com.webtro.modules.admin.dto.request;

import com.webtro.common.enums.ReportSeverity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu khóa tin (canonical 4.14.5). {@code reason} kiểm tra rỗng ở service để trả đúng mã
 * {@code LOCK_LISTING_REASON_REQUIRED}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "LockListingRequest", description = "Yêu cầu khóa tin đăng")
public class LockListingRequest {

    @Schema(description = "Lý do khóa (10–500 ký tự)")
    @Size(min = 10, max = 500, message = "Lý do phải từ 10 đến 500 ký tự")
    private String reason;

    @Schema(description = "Mức độ vi phạm", example = "CRITICAL")
    @NotNull(message = "Vui lòng chọn mức độ vi phạm")
    private ReportSeverity severity;

    @Schema(description = "Thông báo cho chủ trọ", example = "true")
    @Builder.Default
    private Boolean notifyOwner = true;

    @Schema(description = "Ghi cảnh báo vi phạm cho chủ trọ", example = "true")
    @Builder.Default
    private Boolean issueWarning = true;
}
