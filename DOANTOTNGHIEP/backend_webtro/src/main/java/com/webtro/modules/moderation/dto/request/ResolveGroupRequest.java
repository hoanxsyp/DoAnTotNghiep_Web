package com.webtro.modules.moderation.dto.request;

import com.webtro.common.enums.ModerationResult;
import com.webtro.common.enums.ReportTargetType;
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
 * Yêu cầu xử lý cả nhóm báo cáo về cùng một đối tượng bằng một quyết định nhất quán
 * (canonical 4.16.8).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ResolveGroupRequest", description = "Xử lý cả nhóm báo cáo")
public class ResolveGroupRequest {

    @NotNull(message = "Vui lòng chọn loại đối tượng")
    @Schema(description = "Loại đối tượng", example = "LISTING")
    private ReportTargetType targetType;

    @NotNull(message = "Thiếu định danh đối tượng")
    @Positive(message = "Định danh đối tượng không hợp lệ")
    @Schema(description = "Id đối tượng", example = "1024")
    private Long targetId;

    @NotNull(message = "Vui lòng chọn kết quả xử lý")
    @Schema(description = "Kết quả áp cho cả nhóm", example = "MEDIUM_HIDE")
    private ModerationResult result;

    @NotBlank(message = "Vui lòng nhập phản hồi cho người báo cáo")
    @Size(min = 10, max = 500, message = "Phản hồi từ 10 đến 500 ký tự")
    @Schema(description = "Phản hồi gửi mọi người báo cáo")
    private String moderatorResponse;

    @Size(max = 500, message = "Ghi chú nội bộ tối đa 500 ký tự")
    @Schema(description = "Ghi chú nội bộ")
    private String internalNote;

    @Schema(description = "true = chỉ đóng report PENDING/REVIEWING; false = đóng lại cả report đã RESOLVED", example = "true")
    @Builder.Default
    private Boolean onlyPending = true;
}
