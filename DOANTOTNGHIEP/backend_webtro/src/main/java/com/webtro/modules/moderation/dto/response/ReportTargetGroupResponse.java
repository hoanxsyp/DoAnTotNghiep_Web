package com.webtro.modules.moderation.dto.response;

import com.webtro.common.PageResponse;
import com.webtro.common.enums.ReportReason;
import com.webtro.common.enums.ReportSeverity;
import com.webtro.common.enums.ReportStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/**
 * Toàn bộ báo cáo về một đối tượng cụ thể (canonical 4.16.7): bối cảnh đối tượng + tổng hợp + danh
 * sách từng báo cáo, phục vụ Moderator quyết định một lần cho cả nhóm.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ReportTargetGroupResponse", description = "Báo cáo theo đối tượng")
public class ReportTargetGroupResponse {

    private TargetSnapshot target;
    private GroupSummary summary;
    private PageResponse<TargetReportItem> reports;

    /** Ảnh chụp bối cảnh đối tượng. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "TargetSnapshot")
    public static class TargetSnapshot {
        private String type;
        private Long id;
        private String title;
        private String status;
        private String excerpt;
        private Long ownerId;
        private String ownerName;
        private Integer ownerTrustScore;
        private Long ownerWarningCountLast30Days;
    }

    /** Tổng hợp nhóm. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "GroupSummary")
    public static class GroupSummary {
        private long totalCount;
        private long pendingCount;
        private long reviewingCount;
        private long resolvedCount;
        private long rejectedCount;
        private long distinctReporterCount;
        private ReportSeverity highestSeverity;
        private Instant firstReportedAt;
        private Instant lastReportedAt;
        private List<ReasonCount> reasonBreakdown;
        private boolean autoHideThresholdMet;
        private String autoHideThresholdDetail;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "ReasonCount")
    public static class ReasonCount {
        private ReportReason reason;
        private long count;
    }

    /** Một báo cáo trong nhóm. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "TargetReportItem")
    public static class TargetReportItem {
        private Long id;
        private Long reporterId;
        private String reporterName;
        private long reporterReportCount;
        private long reporterRejectedCount;
        private ReportReason reason;
        private ReportSeverity severity;
        private String description;
        private ReportStatus status;
        private Long assignedToId;
        private Instant createdAt;
    }
}
