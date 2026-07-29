package com.webtro.modules.moderation.dto.response;

import com.webtro.common.enums.ModerationResult;
import com.webtro.common.enums.ReportStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Kết quả xử lý một báo cáo (canonical 4.16.4).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ResolveReportResponse", description = "Kết quả xử lý báo cáo")
public class ResolveReportResponse {

    private Long id;
    private ReportStatus status;
    private ReportStatus previousStatus;
    private ModerationResult result;
    private String resultLabel;
    private String moderatorResponse;
    private String warningMessage;
    private Long resolvedById;
    private String resolvedByName;

    /** Hành động đã thực hiện trên đối tượng (ẩn/khóa/gỡ cờ), null nếu không đụng đối tượng. */
    private TargetAction targetAction;

    private boolean warningIssued;
    private Long warningId;
    private Long ownerWarningCountLast30Days;
    private Long ownerLockedListingCountLast60Days;
    private boolean accountLockSuggested;
    private String accountLockSuggestionReason;
    private int relatedReportsResolved;
    private boolean reporterNotified;
    private boolean ownerNotified;
    private Long moderationActionId;
    private Long auditLogId;
    private Instant resolvedAt;

    /**
     * Mô tả tác động lên đối tượng bị báo cáo.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "TargetAction", description = "Hành động trên đối tượng bị báo cáo")
    public static class TargetAction {
        private String type;
        private Long targetId;
        private String previousStatus;
        private String newStatus;
        private String severity;
    }
}
