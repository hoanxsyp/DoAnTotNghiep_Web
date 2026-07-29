package com.webtro.modules.moderation.dto.request;

import com.webtro.common.enums.ReportStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu nhận xử lý báo cáo (canonical 4.16.3): chuyển {@code PENDING → REVIEWING} và gán cho
 * người xử lý hiện tại. Chỉ chấp nhận giá trị {@code REVIEWING} — kết luận đi qua {@code /resolve}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AssignReportRequest", description = "Nhận xử lý báo cáo")
public class AssignReportRequest {

    @NotNull(message = "Vui lòng chọn trạng thái")
    @Schema(description = "Trạng thái mới (chỉ REVIEWING)", example = "REVIEWING")
    private ReportStatus status;

    @Size(max = 500, message = "Ghi chú nội bộ tối đa 500 ký tự")
    @Schema(description = "Ghi chú nội bộ (không lộ ra ngoài)")
    private String internalNote;
}
