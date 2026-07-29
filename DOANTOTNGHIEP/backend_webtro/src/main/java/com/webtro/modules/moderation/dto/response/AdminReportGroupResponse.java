package com.webtro.modules.moderation.dto.response;

import com.webtro.common.enums.ReportSeverity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Một nhóm báo cáo về cùng một đối tượng khi {@code groupBy=TARGET} (canonical 4.16.1).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AdminReportGroupResponse", description = "Nhóm báo cáo theo đối tượng")
public class AdminReportGroupResponse {

    private String targetType;
    private Long targetId;
    private String targetTitle;
    private String targetStatus;
    private Long targetOwnerId;
    private String targetOwnerName;
    private Integer targetOwnerTrustScore;
    private long reportCount;
    private long distinctReporterCount;
    private ReportSeverity maxSeverity;
    private Map<String, Long> reportsByReason;
    private List<Long> reportIds;
    private long pendingCount;
    private Instant firstReportedAt;
    private Instant lastReportedAt;
}
