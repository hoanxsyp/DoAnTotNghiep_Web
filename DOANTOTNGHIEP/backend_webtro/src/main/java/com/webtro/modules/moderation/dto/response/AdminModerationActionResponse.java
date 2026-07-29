package com.webtro.modules.moderation.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.webtro.common.enums.ModerationActionType;
import com.webtro.common.enums.ModerationResult;
import com.webtro.common.enums.ReportTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Một dòng trong nhật ký hành động kiểm duyệt gần đây (canonical §11.4, phục vụ màn lịch sử kiểm
 * duyệt của Admin). Ánh xạ trực tiếp từ {@code moderation_actions} (append-only).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "AdminModerationActionResponse", description = "Hành động kiểm duyệt (nhật ký)")
public class AdminModerationActionResponse {

    private Long id;
    private Long moderatorId;
    private Boolean isSystem;
    private Long reportId;
    private ReportTargetType targetType;
    private String targetTypeLabel;
    private Long targetId;
    private Long listingId;
    private ModerationActionType actionType;
    private String actionTypeLabel;
    private ModerationResult result;
    private String resultLabel;
    private String reason;
    private String note;
    private String previousStatus;
    private String newStatus;
    private Instant createdAt;
}
