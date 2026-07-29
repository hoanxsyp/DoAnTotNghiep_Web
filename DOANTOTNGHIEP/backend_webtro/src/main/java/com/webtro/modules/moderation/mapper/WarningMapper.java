package com.webtro.modules.moderation.mapper;

import com.webtro.modules.moderation.dto.response.WarningItemResponse;
import com.webtro.modules.moderation.entity.ViolationWarning;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Chuyển đổi {@link ViolationWarning} sang DTO (canonical luật 3).
 */
@Component
public class WarningMapper {

    /**
     * Một dòng cảnh báo trong danh sách.
     *
     * @param userName             tên người bị cảnh báo (module user)
     * @param relatedListingTitle  tiêu đề tin liên quan (module listing), có thể null
     * @param issuedByName         tên người ra cảnh báo, có thể null (hệ thống)
     * @param windowStart          mốc bắt đầu cửa sổ tính ngưỡng (để suy {@code withinWindow})
     */
    public WarningItemResponse toItem(ViolationWarning w, String userName, String relatedListingTitle,
                                      String issuedByName, Instant windowStart) {
        return WarningItemResponse.builder()
                .id(w.getId())
                .userId(w.getUserId())
                .userName(userName)
                .reason(w.getReason())
                .severity(w.getSeverity())
                .severityLabel(w.getSeverity().getLabel())
                .relatedListingId(w.getListingId())
                .relatedListingTitle(relatedListingTitle)
                .relatedReportId(w.getReportId())
                .issuedById(w.getIssuedBy())
                .issuedByName(issuedByName)
                .withinWindow(w.getCreatedAt() != null && windowStart != null
                        && !w.getCreatedAt().isBefore(windowStart))
                .acknowledged(w.getAcknowledgedAt() != null)
                .createdAt(w.getCreatedAt())
                .build();
    }
}
