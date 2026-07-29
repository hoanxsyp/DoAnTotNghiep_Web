package com.webtro.modules.moderation.dto.response;

import com.webtro.common.PageResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * Danh sách báo cáo quản trị khi {@code groupBy=NONE}: trang báo cáo kèm bảng đếm theo trạng thái
 * và mức độ (canonical 4.16.1).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AdminReportListResponse", description = "Danh sách báo cáo + thống kê nhanh")
public class AdminReportListResponse {

    private PageResponse<AdminReportItemResponse> page;

    @Schema(description = "Số báo cáo theo từng trạng thái")
    private Map<String, Long> statusCounts;

    @Schema(description = "Số báo cáo theo từng mức độ")
    private Map<String, Long> severityCounts;
}
