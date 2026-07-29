package com.webtro.modules.moderation.dto.response;

import com.webtro.common.enums.ModerationResult;
import com.webtro.common.enums.ReportReason;
import com.webtro.common.enums.ReportSeverity;
import com.webtro.common.enums.ReportStatus;
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
 * Chi tiết một báo cáo kèm ngữ cảnh để Moderator ra quyết định (canonical 4.16.2).
 *
 * <p>Ghi chú phạm vi: các trường phái sinh từ module AI (thống kê cảm xúc của tin) không thuộc dữ
 * liệu module moderation nắm giữ nên không đưa vào đây; {@link #recommendedResult} được suy heuristic
 * từ số liệu report + số tin bị khóa của chủ (dữ liệu module này có).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ReportDetailResponse", description = "Chi tiết báo cáo + ngữ cảnh")
public class ReportDetailResponse {

    private Long id;
    private ReportTargetType targetType;
    private Long targetId;
    private ReportReason reason;
    private String reasonLabel;
    private String description;
    private String evidenceImageUrl;
    private ReportStatus status;
    private ReportSeverity severity;
    private ModerationResult result;
    private String internalNote;
    private Long assignedToId;

    private ReporterInfo reporter;
    private TargetContext target;

    private List<RelatedReport> relatedReports;
    private long relatedReportCount;
    private long distinctReporterCount;

    private List<ModerationHistoryEntry> moderationHistory;

    private ModerationResult recommendedResult;
    private List<String> recommendationBasis;

    private Instant createdAt;
    private Instant resolvedAt;

    /** Ảnh chụp bối cảnh đối tượng bị báo cáo (theo targetType). */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "TargetContext")
    public static class TargetContext {
        private String type;
        private Long id;
        private String title;
        private String status;
        private String excerpt;
        private Long ownerId;
        private String ownerName;
        private Integer ownerTrustScore;
        private Long ownerWarningCountLast30Days;
        private Long ownerLockedListingCountLast60Days;
    }

    /** Một báo cáo khác về cùng đối tượng. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "RelatedReport")
    public static class RelatedReport {
        private Long id;
        private ReportReason reason;
        private ReportSeverity severity;
        private Long reporterId;
        private String description;
        private ReportStatus status;
        private Instant createdAt;
    }

    /** Một dòng lịch sử kiểm duyệt trên đối tượng. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "ModerationHistoryEntry")
    public static class ModerationHistoryEntry {
        private Long id;
        private String type;
        private String reason;
        private Long moderatorId;
        private boolean system;
        private Instant createdAt;
    }
}
