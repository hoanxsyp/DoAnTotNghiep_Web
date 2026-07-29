package com.webtro.modules.moderation.dto.response;

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
 * Kết quả tạo báo cáo (canonical 4.8.1).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ReportResponse", description = "Báo cáo vừa tạo")
public class ReportResponse {

    private Long id;
    private ReportTargetType targetType;
    private Long targetId;
    private String targetTitle;
    private ReportReason reason;
    private String reasonLabel;
    private String description;
    private String evidenceImageUrl;
    private ReportStatus status;
    private ReportSeverity severity;
    private Instant createdAt;
}
