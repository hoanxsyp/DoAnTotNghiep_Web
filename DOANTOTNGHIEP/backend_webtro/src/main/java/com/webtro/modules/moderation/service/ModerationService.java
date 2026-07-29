package com.webtro.modules.moderation.service;

import com.webtro.common.PageResponse;
import com.webtro.common.enums.ReportSeverity;
import com.webtro.common.enums.ReportStatus;
import com.webtro.common.enums.ReportTargetType;
import com.webtro.modules.moderation.dto.request.AssignReportRequest;
import com.webtro.modules.moderation.dto.request.CreateReportRequest;
import com.webtro.modules.moderation.dto.request.CreateWarningRequest;
import com.webtro.modules.moderation.dto.request.ResolveGroupRequest;
import com.webtro.modules.moderation.dto.request.ResolveReportRequest;
import com.webtro.modules.moderation.dto.response.AdminModerationActionResponse;
import com.webtro.modules.moderation.dto.response.AdminReportGroupResponse;
import com.webtro.modules.moderation.dto.response.AdminReportListResponse;
import com.webtro.modules.moderation.dto.response.AssignReportResponse;
import com.webtro.modules.moderation.dto.response.MyReportResponse;
import com.webtro.modules.moderation.dto.response.ReportDetailResponse;
import com.webtro.modules.moderation.dto.response.ReportResponse;
import com.webtro.modules.moderation.dto.response.ReportTargetGroupResponse;
import com.webtro.modules.moderation.dto.response.ResolveGroupResponse;
import com.webtro.modules.moderation.dto.response.ResolveReportResponse;
import com.webtro.modules.moderation.dto.response.WarningItemResponse;
import com.webtro.modules.moderation.dto.response.WarningResponse;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

/**
 * Nghiệp vụ kiểm duyệt: báo cáo vi phạm (RPT-01..06), cảnh báo vi phạm, ghi nhật ký hành động
 * kiểm duyệt và các phép đếm ngưỡng {@code [§5.4]}.
 *
 * <p>Đây là API công khai của module cho controller và cho module khác (canonical luật 4). Mọi
 * ràng buộc nghiệp vụ ({@code RESOLVED} phải có {@code resolved_by/resolved_at/is_valid}; ranh giới
 * quyền {@code LISTING_LOCK}; ngưỡng tự ẩn/khóa) được ép ở tầng hiện thực.
 */
public interface ModerationService {

    // ============================== Người dùng ==============================

    /** Tạo báo cáo vi phạm (canonical 4.8.1). */
    ReportResponse createReport(CreateReportRequest request, Long reporterId);

    /** Danh sách báo cáo của tôi (canonical 4.8.2). */
    PageResponse<MyReportResponse> getMyReports(Long reporterId, List<ReportStatus> statuses,
                                                ReportTargetType targetType, Pageable pageable);

    // ============================== Quản trị ==============================

    /** Danh sách báo cáo phẳng + thống kê (canonical 4.16.1, groupBy=NONE). */
    AdminReportListResponse adminListReports(List<ReportStatus> statuses, List<ReportTargetType> targetTypes,
                                             Long targetId, List<ReportSeverity> severities, Long reporterId,
                                             Instant from, Instant to, Pageable pageable);

    /** Danh sách báo cáo gom nhóm theo đối tượng (canonical 4.16.1, groupBy=TARGET). */
    PageResponse<AdminReportGroupResponse> adminListReportGroups(List<ReportStatus> statuses,
                                                                 List<ReportTargetType> targetTypes,
                                                                 List<ReportSeverity> severities,
                                                                 Instant from, Instant to, Pageable pageable);

    /** Chi tiết một báo cáo kèm ngữ cảnh (canonical 4.16.2). */
    ReportDetailResponse getReportDetail(Long reportId);

    /**
     * Nhật ký hành động kiểm duyệt gần đây (§11.4) — phục vụ màn lịch sử kiểm duyệt của Admin. Lọc
     * tùy chọn theo {@code targetType} và {@code listingId}; mặc định mới nhất trước.
     */
    PageResponse<AdminModerationActionResponse> adminListActions(ReportTargetType targetType,
                                                                Long listingId, Pageable pageable);

    /** Nhận xử lý báo cáo: PENDING → REVIEWING (canonical 4.16.3). */
    AssignReportResponse assignReport(Long reportId, AssignReportRequest request, Long moderatorId);

    /** Xử lý (kết luận) một báo cáo (canonical 4.16.4). */
    ResolveReportResponse resolveReport(Long reportId, ResolveReportRequest request, Long moderatorId);

    /** Toàn bộ báo cáo về một đối tượng (canonical 4.16.7). */
    ReportTargetGroupResponse getReportsByTarget(ReportTargetType targetType, Long targetId,
                                                 List<ReportStatus> statuses, Pageable pageable);

    /** Xử lý cả nhóm báo cáo về cùng đối tượng (canonical 4.16.8). */
    ResolveGroupResponse resolveGroup(ResolveGroupRequest request, Long moderatorId);

    // ============================== Cảnh báo ==============================

    /** Gửi cảnh báo vi phạm trực tiếp (canonical 4.16.5). */
    WarningResponse createWarning(CreateWarningRequest request, Long issuerId);

    /** Danh sách cảnh báo (canonical 4.16.6). */
    PageResponse<WarningItemResponse> listWarnings(Long userId, List<ReportSeverity> severities,
                                                   Long issuedById, boolean activeOnly,
                                                   Instant from, Instant to, Pageable pageable);

    // ===================== Đếm ngưỡng cho module khác =====================

    /** Số cảnh báo của một người dùng trong cửa sổ {@code moderation.threshold.warning_window_days} (§5.4). */
    long countActiveWarnings(Long userId);

    /** Số tin của một chủ trọ bị khóa trong cửa sổ {@code moderation.threshold.locked_listing_window_days} (§5.4). */
    long countRecentLockedListings(Long ownerId);
}
