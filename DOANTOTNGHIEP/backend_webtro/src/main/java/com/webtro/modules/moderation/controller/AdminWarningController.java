package com.webtro.modules.moderation.controller;

import com.webtro.common.ApiResponse;
import com.webtro.common.PageResponse;
import com.webtro.common.enums.ReportSeverity;
import com.webtro.modules.moderation.dto.request.CreateWarningRequest;
import com.webtro.modules.moderation.dto.response.WarningItemResponse;
import com.webtro.modules.moderation.dto.response.WarningResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * API cảnh báo vi phạm (canonical 4.16.5–4.16.6, {@code [§2.8]} RPT-05, {@code [§5.4]}). Quyền
 * {@code WARNING_SEND} (Moderator + Admin).
 */
@RestController
@RequestMapping("/api/admin/warnings")
@RequiredArgsConstructor
@Tag(name = "16. Admin - Report", description = "Báo cáo & cảnh báo (quản trị)")
public class AdminWarningController {

    private final ModerationService moderationService;

    /** Gửi cảnh báo vi phạm cho một người dùng. */
    @PostMapping
    @PreAuthorize("hasAnyRole('MODERATOR','ADMIN')")
    @Operation(summary = "Gửi cảnh báo vi phạm",
            description = "Đủ ngưỡng cảnh báo trong cửa sổ sẽ tự tạm khóa đăng tin; chỉ đề xuất khóa tài khoản.")
    public ResponseEntity<ApiResponse<WarningResponse>> create(
            @Valid @RequestBody CreateWarningRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        WarningResponse data = moderationService.createWarning(request, currentUser.getId());
        return ResponseEntity.created(URI.create("/api/admin/warnings/" + data.getId()))
                .body(ApiResponse.success(data, "Đã gửi cảnh báo"));
    }

    /** Danh sách cảnh báo. */
    @GetMapping
    @PreAuthorize("hasAnyRole('MODERATOR','ADMIN')")
    @Operation(summary = "Danh sách cảnh báo")
    public ResponseEntity<ApiResponse<PageResponse<WarningItemResponse>>> list(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) List<ReportSeverity> severity,
            @RequestParam(required = false) Long issuedById,
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        PageResponse<WarningItemResponse> data = moderationService.listWarnings(
                userId, severity, issuedById, activeOnly, from, to, pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy danh sách cảnh báo thành công"));
    }
}
