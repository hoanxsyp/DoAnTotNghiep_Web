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
 * Kết quả gửi cảnh báo vi phạm (canonical 4.16.5).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "WarningResponse", description = "Cảnh báo vừa gửi")
public class WarningResponse {

    private Long id;
    private Long userId;
    private String userName;
    private String reason;
    private ReportSeverity severity;
    private Long relatedListingId;
    private Long relatedReportId;
    private Long issuedById;
    private String issuedByName;
    private long warningCountLast30Days;
    private int warningThreshold;
    private boolean postingSuspensionTriggered;
    private Instant postingSuspendedUntil;
    private boolean accountLockSuggested;
    private boolean userNotified;
    private boolean emailSent;
    private Instant createdAt;
}
