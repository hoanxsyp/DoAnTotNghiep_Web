package com.webtro.modules.admin.controller;

import com.webtro.common.ApiResponse;
import com.webtro.common.PageResponse;
import com.webtro.common.enums.UserStatus;
import com.webtro.modules.admin.dto.request.LockUserRequest;
import com.webtro.modules.admin.dto.request.UnlockUserRequest;
import com.webtro.modules.admin.dto.request.UpdateRoleRequest;
import com.webtro.modules.admin.dto.response.AdminUserDetailResponse;
import com.webtro.modules.admin.dto.response.AdminUserResponse;
import com.webtro.modules.admin.dto.response.UserActionResponse;
import com.webtro.modules.admin.service.AdminUserService;
import com.webtro.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * API quản trị người dùng (canonical 4.13). Chỉ Admin. Khóa/mở khóa cần {@code USER_MANAGE}; đổi
 * vai trò cần {@code USER_ROLE_ASSIGN}.
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Tag(name = "13. Admin - User", description = "Quản lý người dùng và chủ trọ")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Quản lý người dùng", description = "Tìm/lọc theo từ khóa, vai trò, trạng thái, xác thực, thời gian.")
    public ResponseEntity<ApiResponse<PageResponse<AdminUserResponse>>> searchUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) List<String> role,
            @RequestParam(required = false) List<UserStatus> status,
            @RequestParam(required = false) Boolean verified,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Instant fromInstant = from != null ? from.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        Instant toInstant = to != null ? to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null;

        PageResponse<AdminUserResponse> data = adminUserService.searchUsers(
                keyword, role, status, verified, fromInstant, toInstant, pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy danh sách người dùng thành công"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Chi tiết người dùng", description = "Hồ sơ, vai trò, trạng thái và số liệu liên quan của một người dùng.")
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> getUserDetail(@PathVariable Long id) {
        AdminUserDetailResponse data = adminUserService.getUserDetail(id);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy chi tiết người dùng thành công"));
    }

    @PutMapping("/{id}/lock")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Khóa tài khoản", description = "Bắt buộc lý do; thu hồi phiên; tùy chọn khóa tin; ghi audit.")
    public ResponseEntity<ApiResponse<UserActionResponse>> lockUser(
            @PathVariable Long id,
            @Valid @RequestBody LockUserRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        UserActionResponse data = adminUserService.lockUser(id, request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(data, "Đã khóa tài khoản"));
    }

    @PutMapping("/{id}/unlock")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Mở khóa tài khoản", description = "LOCKED → ACTIVE/PENDING_VERIFY; ghi audit.")
    public ResponseEntity<ApiResponse<UserActionResponse>> unlockUser(
            @PathVariable Long id,
            @Valid @RequestBody UnlockUserRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        UserActionResponse data = adminUserService.unlockUser(id, request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(data, "Đã mở khóa tài khoản"));
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Đổi vai trò", description = "Thay thế vai trò duy nhất của người dùng; ghi audit; thu hồi phiên.")
    public ResponseEntity<ApiResponse<UserActionResponse>> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRoleRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        UserActionResponse data = adminUserService.updateRole(id, request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(data, "Đã cập nhật vai trò người dùng"));
    }
}
