package com.webtro.modules.search.service;

import com.webtro.common.PageResponse;
import com.webtro.modules.listing.dto.response.ListingSummaryResponse;
import com.webtro.modules.search.dto.request.ListingSearchRequest;
import com.webtro.modules.search.dto.response.RelatedListingsResponse;
import com.webtro.security.CustomUserDetails;
import org.springframework.data.domain.Pageable;

/**
 * Nghiệp vụ tìm kiếm &amp; khám phá tin đăng (module SEARCH, SRCH-01..09, {@code [§3.7]}).
 *
 * <p>Chỉ trả tin thỏa {@code ListingVisibilityService.publicStatuses()} (canonical mục 5.2). Việc
 * chuyển {@code Listing} &rarr; {@link ListingSummaryResponse} ủy thác cho mapper của module
 * listing (tái dùng DTO, không tạo lại). Lịch sử tìm kiếm của người đăng nhập được ghi phi đồng bộ
 * qua sự kiện {@code SearchPerformedEvent}.
 */
public interface ListingSearchService {

    /**
     * Tìm kiếm &amp; lọc động tin đăng công khai (SRCH-01..08).
     *
     * @param request  bộ lọc gom mọi tiêu chí
     * @param pageable  phân trang (size bị ép về tối đa 100; sort điều khiển bởi service, không lấy từ pageable)
     * @param principal người dùng đang đăng nhập (có thể {@code null} — khách ẩn danh)
     * @return trang kết quả DTO tóm tắt, tin đẩy còn hạn ưu tiên lên đầu trong phạm vi khớp lọc
     */
    PageResponse<ListingSummaryResponse> search(ListingSearchRequest request,
                                                Pageable pageable,
                                                CustomUserDetails principal);

    /**
     * Tin liên quan tới một tin (SRCH-09): cùng khu vực / loại / khoảng giá, loại chính nó và các
     * tin không public.
     *
     * @param listingId id tin gốc
     * @param size       số tin muốn lấy ({@code null} → mặc định {@code ai.recommendation.size})
     * @param principal người dùng đang đăng nhập (có thể {@code null})
     */
    RelatedListingsResponse related(Long listingId, Integer size, CustomUserDetails principal);

    /**
     * Tin đang hiển thị công khai của một chủ trọ (trang hồ sơ chủ trọ công khai) — lọc theo
     * {@code ownerId} + tập trạng thái public ({@code ListingVisibilityService.publicStatuses()}) +
     * còn hạn, sắp mới nhất trước. Không ghi lịch sử tìm kiếm.
     *
     * @param ownerId  id chủ trọ (owner của tin)
     * @param pageable phân trang (size ép ≤ 100; sort do service quyết định)
     */
    PageResponse<ListingSummaryResponse> listByOwner(Long ownerId, Pageable pageable);
}
