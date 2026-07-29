package com.webtro.modules.catalog.controller;

import com.webtro.common.ApiResponse;
import com.webtro.common.PageResponse;
import com.webtro.constant.PermissionCode;
import com.webtro.modules.catalog.dto.request.AreaImportRequest;
import com.webtro.modules.catalog.dto.request.CreateDistrictRequest;
import com.webtro.modules.catalog.dto.request.CreateProvinceRequest;
import com.webtro.modules.catalog.dto.request.CreateWardRequest;
import com.webtro.modules.catalog.dto.request.ToggleRequest;
import com.webtro.modules.catalog.dto.request.UpdateDistrictRequest;
import com.webtro.modules.catalog.dto.request.UpdateProvinceRequest;
import com.webtro.modules.catalog.dto.request.UpdateWardRequest;
import com.webtro.modules.catalog.dto.response.AdminProvinceResponse;
import com.webtro.modules.catalog.dto.response.AreaImportResponse;
import com.webtro.modules.catalog.dto.response.DistrictResponse;
import com.webtro.modules.catalog.dto.response.ProvinceResponse;
import com.webtro.modules.catalog.dto.response.ToggleResultResponse;
import com.webtro.modules.catalog.dto.response.WardResponse;
import com.webtro.modules.catalog.service.ProvinceService;
import com.webtro.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * API quản trị khu vực hành chính (tỉnh/huyện/xã) — mục 4.17.5–4.17.11, 4.17.18–4.17.20 của
 * {@code docs/03}. Quyền chung {@code CATALOG_MANAGE}. Không có endpoint DELETE cho khu vực: dữ
 * liệu hành chính là dữ liệu tham chiếu, muốn ngừng dùng thì tắt hỗ trợ qua {@code /toggle}
 * (mục 4.17.5–4.17.11 quy tắc 2).
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + PermissionCode.CATALOG_MANAGE + "')")
@Tag(name = "17. Admin - Catalog", description = "Quản trị danh mục, khu vực, tiện ích")
public class AdminLocationController {

    private final ProvinceService provinceService;

    // ---------------- Tỉnh/thành ----------------

    @GetMapping("/provinces")
    @Operation(summary = "Danh sách tỉnh/thành (quản trị, phân trang)")
    public ResponseEntity<ApiResponse<PageResponse<AdminProvinceResponse>>> listProvinces(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean supportedOnly,
            @PageableDefault(size = 20, sort = "code", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                provinceService.getProvincesForAdmin(keyword, supportedOnly, pageable)));
    }

    @PostMapping("/provinces")
    @Operation(summary = "Tạo tỉnh/thành")
    public ResponseEntity<ApiResponse<ProvinceResponse>> createProvince(
            @Valid @RequestBody CreateProvinceRequest request) {
        ProvinceResponse created = provinceService.createProvince(request, currentUserId());
        return ResponseEntity.created(URI.create("/api/admin/provinces/" + created.getId()))
                .body(ApiResponse.success(created, "Tạo tỉnh/thành thành công"));
    }

    @PutMapping("/provinces/{id}")
    @Operation(summary = "Sửa tỉnh/thành (không đổi mã)")
    public ResponseEntity<ApiResponse<ProvinceResponse>> updateProvince(
            @PathVariable Long id, @Valid @RequestBody UpdateProvinceRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                provinceService.updateProvince(id, request, currentUserId()), "Cập nhật tỉnh/thành thành công"));
    }

    @PutMapping("/provinces/{id}/toggle")
    @Operation(summary = "Bật/tắt hỗ trợ đăng tin cho tỉnh/thành")
    public ResponseEntity<ApiResponse<ToggleResultResponse>> toggleProvince(
            @PathVariable Long id, @Valid @RequestBody ToggleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                provinceService.toggleProvince(id, request, currentUserId()), "Cập nhật hỗ trợ khu vực thành công"));
    }

    // ---------------- Quận/huyện ----------------

    @PostMapping("/districts")
    @Operation(summary = "Tạo quận/huyện")
    public ResponseEntity<ApiResponse<DistrictResponse>> createDistrict(
            @Valid @RequestBody CreateDistrictRequest request) {
        DistrictResponse created = provinceService.createDistrict(request, currentUserId());
        return ResponseEntity.created(URI.create("/api/admin/districts/" + created.getId()))
                .body(ApiResponse.success(created, "Tạo quận/huyện thành công"));
    }

    @PutMapping("/districts/{id}")
    @Operation(summary = "Sửa quận/huyện (không đổi mã)")
    public ResponseEntity<ApiResponse<DistrictResponse>> updateDistrict(
            @PathVariable Long id, @Valid @RequestBody UpdateDistrictRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                provinceService.updateDistrict(id, request, currentUserId()), "Cập nhật quận/huyện thành công"));
    }

    @PutMapping("/districts/{id}/toggle")
    @Operation(summary = "Bật/tắt hỗ trợ đăng tin cho quận/huyện")
    public ResponseEntity<ApiResponse<ToggleResultResponse>> toggleDistrict(
            @PathVariable Long id, @Valid @RequestBody ToggleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                provinceService.toggleDistrict(id, request, currentUserId()), "Cập nhật hỗ trợ khu vực thành công"));
    }

    // ---------------- Phường/xã ----------------

    @PostMapping("/wards")
    @Operation(summary = "Tạo phường/xã")
    public ResponseEntity<ApiResponse<WardResponse>> createWard(
            @Valid @RequestBody CreateWardRequest request) {
        WardResponse created = provinceService.createWard(request, currentUserId());
        return ResponseEntity.created(URI.create("/api/admin/wards/" + created.getId()))
                .body(ApiResponse.success(created, "Tạo phường/xã thành công"));
    }

    @PutMapping("/wards/{id}")
    @Operation(summary = "Sửa phường/xã (không đổi mã)")
    public ResponseEntity<ApiResponse<WardResponse>> updateWard(
            @PathVariable Long id, @Valid @RequestBody UpdateWardRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                provinceService.updateWard(id, request, currentUserId()), "Cập nhật phường/xã thành công"));
    }

    @PutMapping("/wards/{id}/toggle")
    @Operation(summary = "Bật/tắt hỗ trợ đăng tin cho phường/xã")
    public ResponseEntity<ApiResponse<ToggleResultResponse>> toggleWard(
            @PathVariable Long id, @Valid @RequestBody ToggleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                provinceService.toggleWard(id, request, currentUserId()), "Cập nhật hỗ trợ khu vực thành công"));
    }

    // ---------------- Import hàng loạt ----------------

    @PostMapping("/areas/import")
    @Operation(summary = "Import khu vực hành chính hàng loạt (JSON)",
            description = "Tạo/cập nhật tỉnh, quận/huyện, phường/xã theo cây JSON lồng nhau; idempotent theo mã (code). "
                    + "Không dùng CSV/Excel.")
    public ResponseEntity<ApiResponse<AreaImportResponse>> importAreas(
            @Valid @RequestBody AreaImportRequest request) {
        AreaImportResponse data = provinceService.importAreas(request, currentUserId());
        return ResponseEntity.ok(ApiResponse.success(data, "Import khu vực thành công"));
    }

    private Long currentUserId() {
        return SecurityUtils.getCurrentUserId().orElse(null);
    }
}
