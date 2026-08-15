package com.webtro.modules.catalog.controller;

import com.webtro.common.ApiResponse;
import com.webtro.modules.catalog.dto.request.CreateCategoryRequest;
import com.webtro.modules.catalog.dto.request.ReorderRequest;
import com.webtro.modules.catalog.dto.request.ToggleRequest;
import com.webtro.modules.catalog.dto.request.UpdateCategoryRequest;
import com.webtro.modules.catalog.dto.response.CategoryResponse;
import com.webtro.modules.catalog.dto.response.ReorderResultResponse;
import com.webtro.modules.catalog.dto.response.ToggleResultResponse;
import com.webtro.modules.catalog.service.CategoryService;
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
 * API quản trị danh mục loại tin — mục 4.17.1–4.17.4, 4.17.16, 4.17.21 của {@code docs/03}.
 * Quyền chung {@code CATALOG_MANAGE} (chỉ Admin — canonical §4.2).
 */
@RestController
@RequestMapping("/api/admin/categories")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "17. Admin - Catalog", description = "Quản trị danh mục, khu vực, tiện ích")
public class AdminCategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "Danh sách danh mục (kể cả đã ẩn)")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> list(
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getCategories(activeOnly)));
    }

    @PostMapping
    @Operation(summary = "Tạo danh mục")
    public ResponseEntity<ApiResponse<CategoryResponse>> create(
            @Valid @RequestBody CreateCategoryRequest request) {
        CategoryResponse created = categoryService.create(request, currentUserId());
        return ResponseEntity.created(URI.create("/api/admin/categories/" + created.getId()))
                .body(ApiResponse.success(created, "Tạo danh mục thành công"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Sửa danh mục (không đổi mã)")
    public ResponseEntity<ApiResponse<CategoryResponse>> update(
            @PathVariable Long id, @Valid @RequestBody UpdateCategoryRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(categoryService.update(id, request, currentUserId()),
                        "Cập nhật danh mục thành công"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Ẩn danh mục (soft-hide; chặn khi còn tin)")
    public ResponseEntity<Void> hide(@PathVariable Long id) {
        categoryService.hide(id, currentUserId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/toggle")
    @Operation(summary = "Bật/tắt hiển thị danh mục")
    public ResponseEntity<ApiResponse<ToggleResultResponse>> toggle(
            @PathVariable Long id, @Valid @RequestBody ToggleRequest request) {
        ToggleResultResponse result = categoryService.toggle(id, request, currentUserId());
        String msg = Boolean.TRUE.equals(result.getActive())
                ? "Đã bật hiển thị danh mục \"" + result.getName() + "\""
                : "Đã tắt hiển thị danh mục \"" + result.getName() + "\"";
        return ResponseEntity.ok(ApiResponse.success(result, msg));
    }

    @PutMapping("/order")
    @Operation(summary = "Sắp xếp thứ tự hiển thị danh mục")
    public ResponseEntity<ApiResponse<ReorderResultResponse>> reorder(
            @Valid @RequestBody ReorderRequest request) {
        ReorderResultResponse result = categoryService.reorder(request, currentUserId());
        return ResponseEntity.ok(ApiResponse.success(result,
                "Đã cập nhật thứ tự hiển thị của " + result.getUpdatedCount() + " danh mục"));
    }

    private Long currentUserId() {
        return SecurityUtils.getCurrentUserId().orElse(null);
    }
}
