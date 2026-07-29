package com.webtro.modules.catalog.service;

import com.webtro.common.enums.AmenityGroup;
import com.webtro.modules.catalog.dto.request.CreateAmenityRequest;
import com.webtro.modules.catalog.dto.request.ReorderRequest;
import com.webtro.modules.catalog.dto.request.ToggleRequest;
import com.webtro.modules.catalog.dto.request.UpdateAmenityRequest;
import com.webtro.modules.catalog.dto.response.AmenityGroupResponse;
import com.webtro.modules.catalog.dto.response.AmenityResponse;
import com.webtro.modules.catalog.dto.response.ReorderResultResponse;
import com.webtro.modules.catalog.dto.response.ToggleResultResponse;

import java.util.Collection;
import java.util.List;

/**
 * Nghiệp vụ tiện ích ({@code amenities}). Phục vụ đọc công khai (có cache), Admin CRUD, và tra cứu
 * cho module listing/search — canonical mục 3 luật 4.
 */
public interface AmenityService {

    // ---------- Công khai / dùng chung ----------

    /**
     * Danh sách tiện ích (phẳng). {@code group != null} → lọc theo nhóm; {@code activeOnly = true}
     * → bỏ tiện ích đã ẩn. Có cache.
     */
    List<AmenityResponse> getAmenities(AmenityGroup group, boolean activeOnly);

    /** Danh sách tiện ích gom theo 4 nhóm cố định — phục vụ panel bộ lọc/form đăng tin. Có cache. */
    List<AmenityGroupResponse> getGroupedAmenities(boolean activeOnly);

    /** Lấy tiện ích theo id; không thấy → {@code AMENITY_NOT_FOUND}. */
    AmenityResponse getById(Long id);

    /** Tiện ích có tồn tại và đang hoạt động không. */
    boolean existsActiveAmenity(Long id);

    /**
     * Kiểm tra tập id tiện ích đều tồn tại và đang hoạt động; trả về DTO tương ứng. Dùng cho
     * LIST-12 khi gán tiện ích cho tin. Có id không hợp lệ → {@code AMENITY_NOT_FOUND}.
     */
    List<AmenityResponse> validateAndGet(Collection<Long> ids);

    // ---------- Admin ----------

    /** Tạo tiện ích. Trùng mã → {@code AMENITY_CODE_DUPLICATE}. */
    AmenityResponse create(CreateAmenityRequest request, Long actorId);

    /** Sửa tiện ích (không đổi mã). Không thấy → {@code AMENITY_NOT_FOUND}. */
    AmenityResponse update(Long id, UpdateAmenityRequest request, Long actorId);

    /**
     * Ẩn tiện ích (soft-hide: {@code is_active = false}). Còn tin đăng dùng tiện ích →
     * {@code AMENITY_IN_USE} (422).
     */
    void hide(Long id, Long actorId);

    /** Bật/tắt hiển thị tiện ích (idempotent). */
    ToggleResultResponse toggle(Long id, ToggleRequest request, Long actorId);

    /** Sắp xếp lại thứ tự hiển thị tiện ích. */
    ReorderResultResponse reorder(ReorderRequest request, Long actorId);
}
