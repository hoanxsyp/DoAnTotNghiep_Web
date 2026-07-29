package com.webtro.modules.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Kết quả các thao tác kiểm duyệt tin: duyệt (4.14.3), từ chối (4.14.4), khóa (4.14.5), mở khóa
 * (4.14.6). Field không liên quan tới thao tác hiện tại bị bỏ khỏi JSON ({@code NON_NULL}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "AdminListingActionResponse", description = "Kết quả thao tác kiểm duyệt tin")
public class AdminListingActionResponse {

    private Long id;
    private String status;
    private String previousStatus;
    private String reason;
    private String reasonCode;
    private String severity;
    private Integer displayDays;
    private Instant publishedAt;
    private Instant expiredAt;
    private Long moderatorId;
    private Long moderationActionId;
    private Long auditLogId;
    private Boolean warningIssued;
    private Boolean ownerNotified;
    private Instant at;
}
