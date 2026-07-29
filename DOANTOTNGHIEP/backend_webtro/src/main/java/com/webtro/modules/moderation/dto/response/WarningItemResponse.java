package com.webtro.modules.moderation.dto.response;

import com.webtro.common.enums.ReportSeverity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Một dòng cảnh báo trong danh sách (canonical 4.16.6).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "WarningItemResponse", description = "Dòng cảnh báo")
public class WarningItemResponse {

    private Long id;
    private Long userId;
    private String userName;
    private String reason;
    private ReportSeverity severity;
    private String severityLabel;
    private Long relatedListingId;
    private String relatedListingTitle;
    private Long relatedReportId;
    private Long issuedById;
    private String issuedByName;
    private boolean withinWindow;
    private boolean acknowledged;
    private Instant createdAt;
}
