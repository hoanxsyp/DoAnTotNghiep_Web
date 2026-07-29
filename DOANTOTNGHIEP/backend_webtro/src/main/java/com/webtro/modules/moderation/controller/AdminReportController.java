package com.webtro.modules.moderation.controller;

import com.webtro.common.ApiResponse;
import com.webtro.common.PageResponse;
import com.webtro.common.enums.ReportSeverity;
import com.webtro.common.enums.ReportStatus;
import com.webtro.common.enums.ReportTargetType;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessException;
import com.webtro.modules.moderation.dto.request.AssignReportRequest;
import com.webtro.modules.moderation.dto.request.ReportGroupBy;
import com.webtro.modules.moderation.dto.request.ResolveGroupRequest;
import com.webtro.modules.moderation.dto.request.ResolveReportRequest;
import com.webtro.modules.moderation.dto.response.AdminReportGroupResponse;
import com.webtro.modules.moderation.dto.response.AdminReportListResponse;
import com.webtro.modules.moderation.dto.response.AssignReportResponse;
import com.webtro.modules.moderation.dto.response.ReportDetailResponse;
import com.webtro.modules.moderation.dto.response.ReportTargetGroupResponse;
import com.webtro.modules.moderation.dto.response.ResolveGroupResponse;
import com.webtro.modules.moderation.dto.response.ResolveReportResponse;
import com.webtro.modules.moderation.service.ModerationService;
import com.webtro.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * API quản trị báo cáo & xử lý kiểm duyệt (canonical 4.16, {@code [§2.8]} RPT-04..06,
 * {@code [§10.8]}). Quyền chung {@code REPORT_RESOLVE} (Moderator + Admin).
 */
@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
@Tag(name = "16. Admin - Report", description = "Báo cáo & cảnh báo (quản trị)")
public class AdminReportController {

    private static final long MAX_RANGE_DAYS = 90;

    private final ModerationService moderationService;

    /**
     * Cho phép {@code targetType} viết thường trong URL/query ({@code /target/listing/1024}) — chuẩn
     * hóa về hoa trước khi ánh xạ enum (canonical 4.16.7).
     */
    @InitBinder
    void allowLowercaseTargetType(WebDataBinder binder) {
        binder.registerCustomEditor(ReportTargetType.class, new java.beans.PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(text == null || text.isBlank()
                        ? null : ReportTargetType.valueOf(text.trim().toUpperCase()));
            }
        });
    }

    /** Danh sách báo cáo (phẳng hoặc gom nhóm theo đối tượng). */
    @GetMapping
    @PreAuthorize("hasAuthority('REPORT_RESOLVE')")
    @Operation(summary = "Danh sách báo cáo",
            description = "groupBy=NONE trả danh sách phẳng + thống kê; groupBy=TARGET trả các nhóm theo đối tượng.")
    public ResponseEntity<ApiResponse<Object>> list(
            @RequestParam(required = false) List<ReportStatus> status,
            @RequestParam(required = false) List<ReportTargetType> targetType,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) List<ReportSeverity> severity,
            @RequestParam(required = false) Long reporterId,
            @RequestParam(required = false, defaultValue = "NONE") ReportGroupBy groupBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        Instant effectiveFrom = from != null ? from : Instant.now().minus(Duration.ofDays(30));
        Instant effectiveTo = to != null ? to : Instant.now();
        if (Duration.between(effectiveFrom, effectiveTo).toDays() > MAX_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.AUDIT_LOG_RANGE_TOO_LARGE);
        }

        if (groupBy == ReportGroupBy.TARGET) {
            PageResponse<AdminReportGroupResponse> data = moderationService.adminListReportGroups(
                    status, targetType, severity, effectiveFrom, effectiveTo, pageable);
            return ResponseEntity.ok(ApiResponse.<Object>success(data, "Lấy danh sách báo cáo theo nhóm thành công"));
        }
        AdminReportListResponse data = moderationService.adminListReports(
                status, targetType, targetId, severity, reporterId, effectiveFrom, effectiveTo, pageable);
        return ResponseEntity.ok(ApiResponse.<Object>success(data, "Lấy danh sách báo cáo thành công"));
    }

    /** Chi tiết báo cáo kèm ngữ cảnh. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('REPORT_RESOLVE')")
    @Operation(summary = "Chi tiết báo cáo")
    public ResponseEntity<ApiResponse<ReportDetailResponse>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                moderationService.getReportDetail(id), "Lấy chi tiết báo cáo thành công"));
    }

    /** Nhận xử lý báo cáo (PENDING → REVIEWING). */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('REPORT_RESOLVE')")
    @Operation(summary = "Nhận xử lý báo cáo")
    public ResponseEntity<ApiResponse<AssignReportResponse>> assign(
            @PathVariable Long id,
            @Valid @RequestBody AssignReportRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(ApiResponse.success(
                moderationService.assignReport(id, request, currentUser.getId()),
                "Bạn đang xử lý báo cáo này"));
    }

    /** Xử lý (kết luận) báo cáo. */
    @PutMapping("/{id}/resolve")
    @PreAuthorize("hasAuthority('REPORT_RESOLVE')")
    @Operation(summary = "Xử lý báo cáo",
            description = "SEVERE_LOCK yêu cầu thêm quyền LISTING_LOCK (chỉ Admin), kiểm ở service.")
    public ResponseEntity<ApiResponse<ResolveReportResponse>> resolve(
            @PathVariable Long id,
            @Valid @RequestBody ResolveReportRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(ApiResponse.success(
                moderationService.resolveReport(id, request, currentUser.getId()), "Đã xử lý báo cáo"));
    }

    /** Toàn bộ báo cáo về một đối tượng. */
    @GetMapping("/target/{targetType}/{targetId}")
    @PreAuthorize("hasAuthority('REPORT_RESOLVE')")
    @Operation(summary = "Báo cáo theo đối tượng")
    public ResponseEntity<ApiResponse<ReportTargetGroupResponse>> byTarget(
            @PathVariable ReportTargetType targetType,
            @PathVariable Long targetId,
            @RequestParam(required = false) List<ReportStatus> status,
            @PageableDefault(size = 50, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                moderationService.getReportsByTarget(targetType, targetId, status, pageable),
                "Lấy báo cáo theo đối tượng thành công"));
    }

    /** Xử lý cả nhóm báo cáo về cùng đối tượng bằng một quyết định. */
    @PutMapping("/resolve-group")
    @PreAuthorize("hasAuthority('REPORT_RESOLVE')")
    @Operation(summary = "Xử lý cả nhóm báo cáo")
    public ResponseEntity<ApiResponse<ResolveGroupResponse>> resolveGroup(
            @Valid @RequestBody ResolveGroupRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(ApiResponse.success(
                moderationService.resolveGroup(request, currentUser.getId()), "Đã xử lý nhóm báo cáo"));
    }
}
