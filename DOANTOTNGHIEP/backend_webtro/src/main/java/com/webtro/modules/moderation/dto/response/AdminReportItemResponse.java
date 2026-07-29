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

/**
 * Một dòng báo cáo trong danh sách quản trị khi {@code groupBy=NONE} (canonical 4.16.1).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AdminReportItemResponse", description = "Dòng báo cáo (quản trị)")
public class AdminReportItemResponse {

    private Long id;
    private ReportTargetType targetType;
    private Long targetId;
    private String targetTitle;
    private Long targetOwnerId;
    private String targetOwnerName;
    private Integer targetOwnerTrustScore;
    private ReportReason reason;
    private String reasonLabel;
    private String description;
    private String evidenceImageUrl;
    private ReportStatus status;
    private String statusLabel;
    private ReportSeverity severity;
    private ReporterInfo reporter;
    private long relatedReportCount;
    private Long assignedToId;
    private ModerationResult result;
    private String internalNote;
    private Instant createdAt;
    private Instant resolvedAt;
}
