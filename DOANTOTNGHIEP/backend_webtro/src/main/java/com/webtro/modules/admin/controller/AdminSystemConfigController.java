package com.webtro.modules.admin.controller;

import com.webtro.common.ApiResponse;
import com.webtro.modules.admin.dto.request.UpdateConfigRequest;
import com.webtro.modules.admin.dto.response.SystemConfigResponse;
import com.webtro.modules.admin.dto.response.UpdateConfigResponse;
import com.webtro.modules.admin.service.AdminSystemConfigService;
import com.webtro.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API cấu hình hệ thống (canonical 4.20.1–4.20.2). Chỉ Admin ({@code SYSTEM_CONFIG_MANAGE}).
 */
@RestController
@RequestMapping("/api/admin/system-configs")
@RequiredArgsConstructor
@Tag(name = "20. Admin - System", description = "Nhật ký kiểm toán, cấu hình hệ thống")
public class AdminSystemConfigController {

    private final AdminSystemConfigService adminSystemConfigService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Xem cấu hình hệ thống", description = "Toàn bộ cấu hình (trừ nhóm ai.*/trust.*), gom nhóm.")
    public ResponseEntity<ApiResponse<SystemConfigResponse>> getConfigs(
            @RequestParam(required = false) String group) {
        SystemConfigResponse data = adminSystemConfigService.getConfigs(group);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy cấu hình hệ thống thành công"));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cập nhật cấu hình hệ thống", description = "Đổi một hoặc nhiều khóa cấu hình; ghi audit bắt buộc.")
    public ResponseEntity<ApiResponse<UpdateConfigResponse>> updateConfigs(
            @Valid @RequestBody UpdateConfigRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        UpdateConfigResponse data = adminSystemConfigService.updateConfigs(request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(data,
                "Đã cập nhật " + data.getUpdated().size() + " cấu hình hệ thống"));
    }
}
