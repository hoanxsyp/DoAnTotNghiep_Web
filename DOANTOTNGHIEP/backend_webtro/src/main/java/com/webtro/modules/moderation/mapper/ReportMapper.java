package com.webtro.modules.moderation.mapper;

import com.webtro.common.enums.ModerationResult;
import com.webtro.modules.moderation.dto.response.AdminReportItemResponse;
import com.webtro.modules.moderation.dto.response.MyReportResponse;
import com.webtro.modules.moderation.dto.response.ReportResponse;
import com.webtro.modules.moderation.dto.response.ReporterInfo;
import com.webtro.modules.moderation.entity.Report;
import org.springframework.stereotype.Component;

/**
 * Chuyển đổi {@link Report} (entity) sang các DTO phản hồi — nơi DUY NHẤT thực hiện việc này
 * (canonical luật 3). Viết thủ công bằng Builder (không MapStruct).
 *
 * <p>Các trường phái sinh từ module khác (tiêu đề tin, tên/điểm uy tín chủ tin, số liệu người báo
 * cáo) và {@code result} (lưu ở {@code moderation_actions}, không ở {@code reports}) được service
 * truyền vào dưới dạng tham số — mapper không tự gọi module khác.
 */
@Component
public class ReportMapper {

    /** Phản hồi ngay sau khi tạo báo cáo. */
    public ReportResponse toReportResponse(Report r, String targetTitle) {
        return ReportResponse.builder()
                .id(r.getId())
                .targetType(r.getTargetType())
                .targetId(r.getTargetId())
                .targetTitle(targetTitle)
                .reason(r.getReason())
                .reasonLabel(r.getReason().getLabel())
                .description(r.getDescription())
                .evidenceImageUrl(r.getEvidenceImageUrl())
                .status(r.getStatus())
                .severity(r.getSeverity())
                .createdAt(r.getCreatedAt())
                .build();
    }

    /** Một dòng "Báo cáo của tôi". {@code result} lấy từ hành động kiểm duyệt (có thể null). */
    public MyReportResponse toMyReport(Report r, String targetTitle, String targetUrl, ModerationResult result) {
        return MyReportResponse.builder()
                .id(r.getId())
                .targetType(r.getTargetType())
                .targetId(r.getTargetId())
                .targetTitle(targetTitle)
                .targetUrl(targetUrl)
                .reason(r.getReason())
                .reasonLabel(r.getReason().getLabel())
                .description(r.getDescription())
                .evidenceImageUrl(r.getEvidenceImageUrl())
                .status(r.getStatus())
                .statusLabel(r.getStatus().getLabel())
                .severity(r.getSeverity())
                .result(result)
                .resultLabel(result == null ? null : result.getLabel())
                .moderatorResponse(r.getResolutionNote())
                .createdAt(r.getCreatedAt())
                .resolvedAt(r.getResolvedAt())
                .build();
    }

    /** Một dòng báo cáo trong danh sách quản trị (groupBy=NONE). */
    public AdminReportItemResponse toAdminItem(Report r, String targetTitle, Long targetOwnerId,
                                               String targetOwnerName, Integer targetOwnerTrustScore,
                                               ReporterInfo reporter, long relatedReportCount,
                                               ModerationResult result, String internalNote) {
        return AdminReportItemResponse.builder()
                .id(r.getId())
                .targetType(r.getTargetType())
                .targetId(r.getTargetId())
                .targetTitle(targetTitle)
                .targetOwnerId(targetOwnerId)
                .targetOwnerName(targetOwnerName)
                .targetOwnerTrustScore(targetOwnerTrustScore)
                .reason(r.getReason())
                .reasonLabel(r.getReason().getLabel())
                .description(r.getDescription())
                .evidenceImageUrl(r.getEvidenceImageUrl())
                .status(r.getStatus())
                .statusLabel(r.getStatus().getLabel())
                .severity(r.getSeverity())
                .reporter(reporter)
                .relatedReportCount(relatedReportCount)
                .assignedToId(r.getResolvedBy())
                .result(result)
                .internalNote(internalNote)
                .createdAt(r.getCreatedAt())
                .resolvedAt(r.getResolvedAt())
                .build();
    }
}
