package com.webtro.modules.catalog.service;

import com.webtro.common.enums.CategoryCode;
import com.webtro.modules.catalog.dto.request.CreateCategoryRequest;
import com.webtro.modules.catalog.dto.request.ReorderRequest;
import com.webtro.modules.catalog.dto.request.ToggleRequest;
import com.webtro.modules.catalog.dto.request.UpdateCategoryRequest;
import com.webtro.modules.catalog.dto.response.CategoryResponse;
import com.webtro.modules.catalog.dto.response.ReorderResultResponse;
import com.webtro.modules.catalog.dto.response.ToggleResultResponse;

import java.util.List;

/**
 * Nghiệp vụ danh mục loại tin ({@code categories}). Vừa phục vụ đọc công khai (có cache), vừa phục
 * vụ Admin CRUD (quyền {@code CATALOG_MANAGE}), vừa cung cấp API tra cứu cho module khác
 * (listing/search) — canonical mục 3 luật 4: module khác chỉ gọi qua interface này.
 */
public interface CategoryService {

    // ---------- Công khai / dùng chung ----------

    /**
     * Danh sách danh mục. {@code activeOnly = true} → chỉ danh mục đang hiển thị (dùng cho form
     * đăng tin/bộ lọc); {@code false} → cả danh mục đã ẩn (dùng cho Admin). Có cache.
     */
    List<CategoryResponse> getCategories(boolean activeOnly);

    /** Lấy danh mục theo id; không thấy → {@code CATEGORY_NOT_FOUND}. */
    CategoryResponse getById(Long id);

    /** Lấy danh mục theo mã enum; không thấy → {@code CATEGORY_NOT_FOUND}. */
    CategoryResponse findByCode(CategoryCode code);

    /** Danh mục có tồn tại và đang hoạt động không (module listing dùng khi validate tin). */
    boolean existsActiveCategory(Long id);

    /**
     * Danh sách trường bắt buộc theo loại tin (đọc {@code required_fields}) — module listing dùng
     * để kiểm tra {@code REQUIRED_FIELD_MISSING} {@code [§10.5]}.
     */
    List<String> getRequiredFields(Long id);

    // ---------- Admin ----------

    /** Tạo danh mục mới. Trùng mã → {@code CATEGORY_CODE_DUPLICATE}. */
    CategoryResponse create(CreateCategoryRequest request, Long actorId);

    /** Sửa danh mục (không đổi mã). Không thấy → {@code CATEGORY_NOT_FOUND}. */
    CategoryResponse update(Long id, UpdateCategoryRequest request, Long actorId);

    /**
     * Ẩn danh mục (soft-hide: {@code is_active = false}). Còn tin đăng tham chiếu →
     * {@code CATEGORY_IN_USE} (422).
     */
    void hide(Long id, Long actorId);

    /** Bật/tắt hiển thị danh mục (idempotent). */
    ToggleResultResponse toggle(Long id, ToggleRequest request, Long actorId);

    /** Sắp xếp lại thứ tự hiển thị danh mục (một giao dịch cho toàn tập). */
    ReorderResultResponse reorder(ReorderRequest request, Long actorId);
}
