package com.webtro.modules.listing.service;

import com.webtro.common.PageResponse;
import com.webtro.common.enums.RentalUnitStatus;
import com.webtro.modules.listing.dto.request.RentalUnitRequest;
import com.webtro.modules.listing.dto.response.ListingCreateResponse;
import com.webtro.modules.listing.dto.response.RentalUnitDuplicateCheckResponse;
import com.webtro.modules.listing.dto.response.RentalUnitResponse;
import org.springframework.data.domain.Pageable;

/**
 * Màn "Phòng của tôi" ({@code /api/landlord/rental-units}) — docs/DANH_GIA_THEO_PHONG_DOI_CHIEU.md
 * mục 4. Mọi thao tác chỉ trên phòng của người đang đăng nhập (ADMIN được sửa phòng đã khoá địa chỉ).
 */
public interface RentalUnitService {

    PageResponse<RentalUnitResponse> listMine(RentalUnitStatus status, String keyword, Pageable pageable);

    RentalUnitResponse get(Long id);

    RentalUnitResponse create(RentalUnitRequest request);

    /** Đổi tên/địa chỉ/số phòng. Phòng đã có đánh giá thì khoá địa chỉ và số phòng (RU-4). */
    RentalUnitResponse update(Long id, RentalUnitRequest request);

    /** Lưu trữ phòng thay cho xoá (RU-5). Phòng còn tin sống thì không lưu trữ được. */
    RentalUnitResponse archive(Long id);

    RentalUnitResponse unarchive(Long id);

    RentalUnitDuplicateCheckResponse checkDuplicate(Long wardId, String addressDetail, String unitLabel,
                                                    Long excludeUnitId);

    /** Đăng lại phòng: tạo tin nháp mới từ tin gần nhất của phòng. */
    ListingCreateResponse relist(Long id);
}
