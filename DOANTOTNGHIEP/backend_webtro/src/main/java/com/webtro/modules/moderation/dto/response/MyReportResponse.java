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
 * Một dòng trong "Báo cáo của tôi" (canonical 4.8.2). Người báo cáo chỉ thấy {@code moderatorResponse}
 * (phản hồi công khai), KHÔNG thấy ghi chú nội bộ của Moderator.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "MyReportResponse", description = "Báo cáo của tôi")
public class MyReportResponse {

    private Long id;
    private ReportTargetType targetType;
    private Long targetId;
    private String targetTitle;
    private String targetUrl;
    private ReportReason reason;
    private String reasonLabel;
    private String description;
    private String evidenceImageUrl;
    private ReportStatus status;
    private String statusLabel;
    private ReportSeverity severity;
    private ModerationResult result;
    private String resultLabel;
    private String moderatorResponse;
    private Instant createdAt;
    private Instant resolvedAt;
}
