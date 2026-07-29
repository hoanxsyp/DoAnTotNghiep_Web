package com.webtro.modules.moderation.mapper;

import com.webtro.modules.moderation.dto.response.AdminModerationActionResponse;
import com.webtro.modules.moderation.entity.ModerationAction;
import org.springframework.stereotype.Component;

/**
 * Chuyển đổi {@link ModerationAction} sang DTO nhật ký kiểm duyệt (canonical luật 3, không trả
 * entity ra controller).
 */
@Component
public class ModerationActionMapper {

    public AdminModerationActionResponse toResponse(ModerationAction a) {
        return AdminModerationActionResponse.builder()
                .id(a.getId())
                .moderatorId(a.getModeratorId())
                .isSystem(a.getIsSystem())
                .reportId(a.getReportId())
                .targetType(a.getTargetType())
                .targetTypeLabel(a.getTargetType() != null ? a.getTargetType().getLabel() : null)
                .targetId(a.getTargetId())
                .listingId(a.getListingId())
                .actionType(a.getActionType())
                .actionTypeLabel(a.getActionType() != null ? a.getActionType().getLabel() : null)
                .result(a.getResult())
                .resultLabel(a.getResult() != null ? a.getResult().getLabel() : null)
                .reason(a.getReason())
                .note(a.getNote())
                .previousStatus(a.getPreviousStatus())
                .newStatus(a.getNewStatus())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
