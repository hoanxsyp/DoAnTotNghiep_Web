package com.webtro.modules.moderation.dto.response;

import com.webtro.common.enums.ModerationResult;
import com.webtro.common.enums.ReportTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/**
 * Kết quả xử lý cả nhóm báo cáo (canonical 4.16.8).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ResolveGroupResponse", description = "Kết quả xử lý cả nhóm báo cáo")
public class ResolveGroupResponse {

    private ReportTargetType targetType;
    private Long targetId;
    private ModerationResult result;
    private List<Long> resolvedReportIds;
    private int resolvedCount;
    private int skippedCount;
    private List<Skipped> skipped;
    private ResolveReportResponse.TargetAction targetAction;
    private boolean warningIssued;
    private Long warningId;
    private int reportersNotified;
    private boolean ownerNotified;
    private List<Long> auditLogIds;
    private Long resolvedById;
    private Instant resolvedAt;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "SkippedReport")
    public static class Skipped {
        private Long id;
        private String reason;
    }
}
