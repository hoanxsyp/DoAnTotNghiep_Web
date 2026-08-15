package com.webtro.modules.moderation.controller;

import com.webtro.common.ApiResponse;
import com.webtro.common.PageResponse;
import com.webtro.common.enums.ReportStatus;
import com.webtro.common.enums.ReportTargetType;
import com.webtro.modules.moderation.dto.request.CreateReportRequest;
import com.webtro.modules.moderation.dto.response.MyReportResponse;
import com.webtro.modules.moderation.dto.response.ReportResponse;
import com.webtro.modules.moderation.service.ModerationService;
import com.webtro.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * API báo cáo vi phạm dành cho người dùng (canonical 4.8, {@code [§2.8]} RPT-01..03, {@code [§12.7]}).
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "08. Report", description = "Báo cáo vi phạm")
public class ReportController {

    private final ModerationService moderationService;

    /**
     * Tạo báo cáo vi phạm (tin/bình luận/người dùng/đánh giá). Nhận {@code multipart/form-data} để
     * kèm ảnh bằng chứng.
     */
    @PostMapping(consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    @PreAuthorize("hasAnyRole('TENANT','LANDLORD','MODERATOR','ADMIN')")
    @Operation(summary = "Tạo báo cáo vi phạm",
            description = "Chống trùng theo (đối tượng, lý do); rate limit spam.report.daily; "
                    + "vượt ngưỡng 5 báo cáo/5 tài khoản/24h thì tự gắn cờ tin NEED_REVIEW.")
    public ResponseEntity<ApiResponse<ReportResponse>> createReport(
            @Valid @ModelAttribute CreateReportRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        ReportResponse data = moderationService.createReport(request, currentUser.getId());
        return ResponseEntity.created(URI.create("/api/reports/" + data.getId()))
                .body(ApiResponse.success(data,
                        "Đã gửi báo cáo. Chúng tôi sẽ xem xét trong thời gian sớm nhất."));
    }

    /** Danh sách báo cáo của tôi. */
    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('TENANT','LANDLORD','MODERATOR','ADMIN')")
    @Operation(summary = "Báo cáo của tôi")
    public ResponseEntity<ApiResponse<PageResponse<MyReportResponse>>> myReports(
            @RequestParam(required = false) List<ReportStatus> status,
            @RequestParam(required = false) ReportTargetType targetType,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        PageResponse<MyReportResponse> data = moderationService.getMyReports(
                currentUser.getId(), status, targetType, pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy danh sách báo cáo của bạn thành công"));
    }
}
