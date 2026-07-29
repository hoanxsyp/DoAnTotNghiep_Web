package com.webtro.modules.catalog.service;

import com.webtro.common.PageResponse;
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
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Nghiệp vụ cây địa chính (tỉnh → huyện → xã). Một service duy nhất cho cả ba cấp vì chúng tạo
 * thành một cây tra cứu thống nhất. Phục vụ đọc công khai (có cache), Admin CRUD, và cung cấp API
 * kiểm tra cho module listing (khu vực có được hỗ trợ đăng tin không) — canonical mục 3 luật 4.
 */
public interface ProvinceService {

    // ---------- Đọc công khai (có cache) ----------

    /**
     * Danh sách tỉnh/thành. {@code supportedOnly = true} → chỉ khu vực hỗ trợ đăng tin
     * {@code [§3.3]}; {@code keyword} lọc theo tên không dấu.
     */
    List<ProvinceResponse> getProvinces(boolean supportedOnly, String keyword);

    /** Quận/huyện đang hoạt động của một tỉnh. Tỉnh không tồn tại → {@code PROVINCE_NOT_FOUND}. */
    List<DistrictResponse> getDistricts(Long provinceId, String keyword);

    /** Phường/xã đang hoạt động của một quận. Quận không tồn tại → {@code DISTRICT_NOT_FOUND}. */
    List<WardResponse> getWards(Long districtId, String keyword);

    // ---------- Tra cứu cho module khác ----------

    /** Tỉnh có tồn tại không. */
    boolean existsProvince(Long id);

    /** Quận/huyện có tồn tại không. */
    boolean existsDistrict(Long id);

    /** Phường/xã có tồn tại không. */
    boolean existsWard(Long id);

    /**
     * Kiểm tra một địa chỉ (tỉnh + huyện + xã) có thuộc khu vực hệ thống hỗ trợ đăng tin không.
     * Đi từ ward → district → province, chỉ cần một cấp {@code is_active = false} là không hỗ trợ
     * (mục 4.17.16–4.17.20 quy tắc 4). {@code districtId}/{@code wardId} có thể null (chỉ tới cấp
     * tỉnh). Id không tồn tại → ném {@code PROVINCE/DISTRICT/WARD_NOT_FOUND}.
     */
    boolean isAreaSupported(Long provinceId, Long districtId, Long wardId);

    // ---------- Admin ----------

    /** Danh sách tỉnh/thành cho màn hình quản trị (phân trang, lọc theo keyword/supportedOnly). */
    PageResponse<AdminProvinceResponse> getProvincesForAdmin(String keyword, Boolean supportedOnly,
                                                             Pageable pageable);

    ProvinceResponse createProvince(CreateProvinceRequest request, Long actorId);

    ProvinceResponse updateProvince(Long id, UpdateProvinceRequest request, Long actorId);

    ToggleResultResponse toggleProvince(Long id, ToggleRequest request, Long actorId);

    DistrictResponse createDistrict(CreateDistrictRequest request, Long actorId);

    DistrictResponse updateDistrict(Long id, UpdateDistrictRequest request, Long actorId);

    ToggleResultResponse toggleDistrict(Long id, ToggleRequest request, Long actorId);

    WardResponse createWard(CreateWardRequest request, Long actorId);

    WardResponse updateWard(Long id, UpdateWardRequest request, Long actorId);

    ToggleResultResponse toggleWard(Long id, ToggleRequest request, Long actorId);

    /**
     * Import khu vực hành chính hàng loạt bằng JSON (tỉnh → huyện → xã). Idempotent theo {@code code}:
     * node đã tồn tại được cập nhật, node mới được tạo. Trả về số node tạo/cập nhật theo từng cấp.
     */
    AreaImportResponse importAreas(AreaImportRequest request, Long actorId);
}
