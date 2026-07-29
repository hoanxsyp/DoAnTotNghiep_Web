package com.webtro.modules.moderation.dto.response;

import com.webtro.common.enums.ReportStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Kết quả nhận xử lý báo cáo (canonical 4.16.3).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AssignReportResponse", description = "Kết quả nhận xử lý báo cáo")
public class AssignReportResponse {

    private Long id;
    private ReportStatus status;
    private ReportStatus previousStatus;
    private Long assignedToId;
    private String assignedToName;
    private int relatedReportsAlsoAssigned;
    private Instant assignedAt;
}
