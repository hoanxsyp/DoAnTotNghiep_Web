package com.webtro.modules.catalog.controller;

import com.webtro.common.ApiResponse;
import com.webtro.common.enums.AmenityGroup;
import com.webtro.modules.catalog.dto.response.AmenityResponse;
import com.webtro.modules.catalog.dto.response.CategoryResponse;
import com.webtro.modules.catalog.dto.response.DistrictResponse;
import com.webtro.modules.catalog.dto.response.ProvinceResponse;
import com.webtro.modules.catalog.dto.response.WardResponse;
import com.webtro.modules.catalog.service.AmenityService;
import com.webtro.modules.catalog.service.CategoryService;
import com.webtro.modules.catalog.service.ProvinceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API công khai (anonymous) cho danh mục, cây địa chính và tiện ích — mục 4.3 của {@code docs/03}.
 * Toàn bộ đọc từ service có cache Redis; Admin sửa sẽ invalidate cache tương ứng.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "03. Catalog", description = "Danh mục, khu vực, tiện ích (công khai)")
public class CatalogController {

    private final CategoryService categoryService;
    private final ProvinceService provinceService;
    private final AmenityService amenityService;

    @GetMapping("/categories")
    @Operation(summary = "Danh sách loại tin đăng",
            description = "Trả về danh mục loại tin. activeOnly=true (mặc định) bỏ danh mục đã ẩn.")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategories(
            @Parameter(description = "Chỉ lấy danh mục đang hiển thị")
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        List<CategoryResponse> data = categoryService.getCategories(activeOnly);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy danh mục thành công"));
    }

    @GetMapping("/provinces")
    @Operation(summary = "Danh sách tỉnh/thành",
            description = "supportedOnly=true chỉ lấy khu vực hệ thống hỗ trợ đăng tin; keyword tìm không dấu.")
    public ResponseEntity<ApiResponse<List<ProvinceResponse>>> getProvinces(
            @Parameter(description = "Chỉ lấy khu vực hỗ trợ đăng tin")
            @RequestParam(defaultValue = "false") boolean supportedOnly,
            @Parameter(description = "Từ khóa tìm theo tên (không dấu), ≤ 50 ký tự")
            @RequestParam(required = false) String keyword) {
        List<ProvinceResponse> data = provinceService.getProvinces(supportedOnly, safeKeyword(keyword));
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy danh sách tỉnh/thành thành công"));
    }

    @GetMapping("/provinces/{id}/districts")
    @Operation(summary = "Quận/huyện theo tỉnh")
    public ResponseEntity<ApiResponse<List<DistrictResponse>>> getDistricts(
            @Parameter(description = "Id tỉnh/thành") @PathVariable Long id,
            @RequestParam(required = false) String keyword) {
        List<DistrictResponse> data = provinceService.getDistricts(id, safeKeyword(keyword));
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy danh sách quận/huyện thành công"));
    }

    @GetMapping("/districts/{id}/wards")
    @Operation(summary = "Phường/xã theo quận")
    public ResponseEntity<ApiResponse<List<WardResponse>>> getWards(
            @Parameter(description = "Id quận/huyện") @PathVariable Long id,
            @RequestParam(required = false) String keyword) {
        List<WardResponse> data = provinceService.getWards(id, safeKeyword(keyword));
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy danh sách phường/xã thành công"));
    }

    @GetMapping("/amenities")
    @Operation(summary = "Danh sách tiện ích",
            description = "Mặc định trả mảng phẳng (mục 4.3.5). grouped=true trả theo nhóm "
                    + "(AmenityGroupResponse) cho panel bộ lọc/form đăng tin.")
    public ResponseEntity<ApiResponse<Object>> getAmenities(
            @Parameter(description = "Lọc theo nhóm tiện ích")
            @RequestParam(required = false) AmenityGroup group,
            @Parameter(description = "Chỉ lấy tiện ích đang hiển thị")
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @Parameter(description = "Gom theo nhóm thay vì mảng phẳng")
            @RequestParam(defaultValue = "false") boolean grouped) {
        Object data = grouped
                ? amenityService.getGroupedAmenities(activeOnly)
                : amenityService.getAmenities(group, activeOnly);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy danh sách tiện ích thành công"));
    }

    // Cắt keyword quá dài để không phá cache và tránh quét vô nghĩa (ràng buộc ≤ 50 ký tự).
    private String safeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > 50 ? trimmed.substring(0, 50) : trimmed;
    }
}
