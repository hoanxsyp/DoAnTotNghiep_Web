package com.webtro.modules.catalog.controller;

import com.webtro.common.ApiResponse;
import com.webtro.common.enums.AmenityGroup;
import com.webtro.modules.catalog.dto.request.CreateAmenityRequest;
import com.webtro.modules.catalog.dto.request.ReorderRequest;
import com.webtro.modules.catalog.dto.request.ToggleRequest;
import com.webtro.modules.catalog.dto.request.UpdateAmenityRequest;
import com.webtro.modules.catalog.dto.response.AmenityResponse;
import com.webtro.modules.catalog.dto.response.ReorderResultResponse;
import com.webtro.modules.catalog.dto.response.ToggleResultResponse;
import com.webtro.modules.catalog.service.AmenityService;
import com.webtro.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * API quản trị tiện ích — mục 4.17.12–4.17.15, 4.17.17, 4.17.22 của {@code docs/03}.
 * Quyền chung {@code CATALOG_MANAGE}.
 */
@RestController
@RequestMapping("/api/admin/amenities")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + com.webtro.constant.PermissionCode.CATALOG_MANAGE + "')")
@Tag(name = "17. Admin - Catalog", description = "Quản trị danh mục, khu vực, tiện ích")
public class AdminAmenityController {

    private final AmenityService amenityService;

    @GetMapping
    @Operation(summary = "Danh sách tiện ích (kể cả đã ẩn)")
    public ResponseEntity<ApiResponse<List<AmenityResponse>>> list(
            @RequestParam(required = false) AmenityGroup group,
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(ApiResponse.success(amenityService.getAmenities(group, activeOnly)));
    }

    @PostMapping
    @Operation(summary = "Tạo tiện ích")
    public ResponseEntity<ApiResponse<AmenityResponse>> create(
            @Valid @RequestBody CreateAmenityRequest request) {
        AmenityResponse created = amenityService.create(request, currentUserId());
        return ResponseEntity.created(URI.create("/api/admin/amenities/" + created.getId()))
                .body(ApiResponse.success(created, "Tạo tiện ích thành công"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Sửa tiện ích (không đổi mã)")
    public ResponseEntity<ApiResponse<AmenityResponse>> update(
            @PathVariable Long id, @Valid @RequestBody UpdateAmenityRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(amenityService.update(id, request, currentUserId()),
                        "Cập nhật tiện ích thành công"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Ẩn tiện ích (soft-hide)")
    public ResponseEntity<Void> hide(@PathVariable Long id) {
        amenityService.hide(id, currentUserId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/toggle")
    @Operation(summary = "Bật/tắt hiển thị tiện ích")
    public ResponseEntity<ApiResponse<ToggleResultResponse>> toggle(
            @PathVariable Long id, @Valid @RequestBody ToggleRequest request) {
        ToggleResultResponse result = amenityService.toggle(id, request, currentUserId());
        String msg = Boolean.TRUE.equals(result.getActive())
                ? "Đã bật hiển thị tiện ích \"" + result.getName() + "\""
                : "Đã tắt hiển thị tiện ích \"" + result.getName() + "\"";
        return ResponseEntity.ok(ApiResponse.success(result, msg));
    }

    @PutMapping("/order")
    @Operation(summary = "Sắp xếp thứ tự hiển thị tiện ích")
    public ResponseEntity<ApiResponse<ReorderResultResponse>> reorder(
            @Valid @RequestBody ReorderRequest request) {
        ReorderResultResponse result = amenityService.reorder(request, currentUserId());
        return ResponseEntity.ok(ApiResponse.success(result,
                "Đã cập nhật thứ tự hiển thị của " + result.getUpdatedCount() + " tiện ích"));
    }

    private Long currentUserId() {
        return SecurityUtils.getCurrentUserId().orElse(null);
    }
}
