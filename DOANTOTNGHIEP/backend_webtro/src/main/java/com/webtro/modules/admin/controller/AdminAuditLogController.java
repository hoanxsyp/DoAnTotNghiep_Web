package com.webtro.modules.admin.controller;

import com.webtro.common.ApiResponse;
import com.webtro.common.PageResponse;
import com.webtro.common.enums.AuditAction;
import com.webtro.modules.admin.dto.response.AuditLogResponse;
import com.webtro.modules.admin.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * API tra cứu nhật ký kiểm toán (canonical 4.20.3). Chỉ Admin ({@code AUDIT_LOG_VIEW}). Audit log là
 * CHỈ-ĐỌC: không có POST/PUT/DELETE — bảo toàn tính toàn vẹn kiểm toán.
 */
@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
@Tag(name = "20. Admin - System", description = "Nhật ký kiểm toán, cấu hình hệ thống")
public class AdminAuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Nhật ký kiểm toán",
            description = "Lọc theo hành động, người thực hiện, đối tượng và khoảng thời gian (trần 90 ngày).")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> query(
            @RequestParam(required = false) List<AuditAction> action,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {

        Instant fromInstant = from != null ? from.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        Instant toInstant = to != null ? to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null;

        PageResponse<AuditLogResponse> data = auditLogService.query(
                action, actorId, targetType, targetId, fromInstant, toInstant, pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy nhật ký kiểm toán thành công"));
    }
}
