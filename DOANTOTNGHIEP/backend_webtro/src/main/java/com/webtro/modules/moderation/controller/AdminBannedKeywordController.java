package com.webtro.modules.moderation.controller;

import com.webtro.common.ApiResponse;
import com.webtro.common.PageResponse;
import com.webtro.common.enums.BannedKeywordScope;
import com.webtro.common.enums.BannedKeywordSeverity;
import com.webtro.modules.moderation.dto.request.BannedKeywordRequest;
import com.webtro.modules.moderation.dto.request.ToggleBannedKeywordRequest;
import com.webtro.modules.moderation.dto.response.BannedKeywordResponse;
import com.webtro.modules.moderation.dto.response.ToggleBannedKeywordResponse;
import com.webtro.modules.moderation.service.BannedKeywordService;
import com.webtro.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * API quản trị từ khóa cấm (canonical 4.20.4–4.20.8, {@code [§3.3][§5.3][§11.10]}).
 *
 * <p>Quyền: chấp nhận {@code SYSTEM_CONFIG_MANAGE} hoặc {@code CATALOG_MANAGE} (đều chỉ Admin theo
 * canonical §4.2) — bám theo yêu cầu đề bài module này.
 */
@RestController
@RequestMapping("/api/admin/banned-keywords")
@RequiredArgsConstructor
@Tag(name = "20. Admin - System", description = "Cấu hình hệ thống & từ khóa cấm")
public class AdminBannedKeywordController {

    private final BannedKeywordService bannedKeywordService;

    /** Danh sách từ khóa cấm. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Danh sách từ khóa cấm")
    public ResponseEntity<ApiResponse<PageResponse<BannedKeywordResponse>>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) List<BannedKeywordSeverity> severity,
            @RequestParam(required = false) List<BannedKeywordScope> scope,
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        PageResponse<BannedKeywordResponse> data = bannedKeywordService.list(
                keyword, severity, scope, activeOnly, pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy danh sách từ khóa cấm thành công"));
    }

    /** Thêm từ khóa cấm. */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Thêm từ khóa cấm")
    public ResponseEntity<ApiResponse<BannedKeywordResponse>> create(
            @Valid @RequestBody BannedKeywordRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        BannedKeywordResponse data = bannedKeywordService.create(request, currentUser.getId());
        return ResponseEntity.created(URI.create("/api/admin/banned-keywords/" + data.getId()))
                .body(ApiResponse.success(data, "Đã thêm từ khóa cấm"));
    }

    /** Sửa từ khóa cấm. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Sửa từ khóa cấm")
    public ResponseEntity<ApiResponse<BannedKeywordResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody BannedKeywordRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(ApiResponse.success(
                bannedKeywordService.update(id, request, currentUser.getId()), "Đã cập nhật từ khóa cấm"));
    }

    /** Gỡ (xóa mềm) từ khóa cấm. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Gỡ từ khóa cấm")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        bannedKeywordService.delete(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    /** Tạm bật/tắt từ khóa cấm. */
    @PutMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Bật/tắt từ khóa cấm")
    public ResponseEntity<ApiResponse<ToggleBannedKeywordResponse>> toggle(
            @PathVariable Long id,
            @Valid @RequestBody ToggleBannedKeywordRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        ToggleBannedKeywordResponse data = bannedKeywordService.toggle(id, request, currentUser.getId());
        String msg = data.isActive() ? "Đã bật từ khóa cấm" : "Đã tạm tắt từ khóa cấm";
        return ResponseEntity.ok(ApiResponse.success(data, msg));
    }
}
