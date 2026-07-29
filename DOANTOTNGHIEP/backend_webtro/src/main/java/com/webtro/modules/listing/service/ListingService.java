package com.webtro.modules.listing.service;

import com.webtro.common.PageResponse;
import com.webtro.common.enums.ListingStatus;
import com.webtro.modules.listing.dto.request.AmenityUpdateRequest;
import com.webtro.modules.listing.dto.request.CloseListingRequest;
import com.webtro.modules.listing.dto.request.ImageOrderRequest;
import com.webtro.modules.listing.dto.request.ListingCreateRequest;
import com.webtro.modules.listing.dto.request.ListingUpdateRequest;
import com.webtro.modules.listing.dto.request.RenewListingRequest;
import com.webtro.modules.listing.dto.response.ImageUploadResponse;
import com.webtro.modules.listing.dto.response.ListingActionResponse;
import com.webtro.modules.listing.dto.response.ListingAmenityResponse;
import com.webtro.modules.listing.dto.response.ListingCreateResponse;
import com.webtro.modules.listing.dto.response.ListingDetailResponse;
import com.webtro.modules.listing.dto.response.ListingStatsResponse;
import com.webtro.modules.listing.dto.response.ListingSummaryResponse;
import com.webtro.modules.listing.dto.response.ModerationImpactResponse;
import com.webtro.modules.listing.entity.Listing;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Nghiệp vụ tin đăng (docs/03 mục 4.4, §3.3–3.6, §5.1). Kiểm quyền permission ở controller
 * ({@code @PreAuthorize}); kiểm quyền sở hữu + quy tắc nghiệp vụ ở tầng service này.
 */
public interface ListingService {

    // ---------------- CRUD ----------------

    ListingCreateResponse create(ListingCreateRequest request);

    ListingDetailResponse update(Long id, ListingUpdateRequest request);

    void delete(Long id);

    // ---------------- Chuyển trạng thái (qua ListingStateMachine) ----------------

    ListingActionResponse submit(Long id);

    ListingActionResponse hide(Long id);

    ListingActionResponse unhide(Long id);

    ListingActionResponse close(Long id, CloseListingRequest request);

    com.webtro.modules.listing.dto.response.RenewResponse renew(Long id, RenewListingRequest request);

    // ---------------- Đọc ----------------

    ListingDetailResponse getDetail(Long id, boolean countView, String clientIp);

    PageResponse<ListingSummaryResponse> getMyListings(List<ListingStatus> statuses, String keyword,
                                                       Long categoryId, Integer expiringWithinDays,
                                                       Pageable pageable);

    ListingStatsResponse getStats(Long id, LocalDate from, LocalDate to);

    // ---------------- Ảnh & tiện ích ----------------

    ImageUploadResponse uploadImages(Long id, List<MultipartFile> files, Integer setPrimaryIndex);

    void deleteImage(Long id, Long imageId, Long newPrimaryImageId);

    ModerationImpactResponse setPrimaryImage(Long id, Long imageId);

    List<ListingImageResponseOrder> reorderImages(Long id, ImageOrderRequest request);

    List<ListingAmenityResponse> updateAmenities(Long id, AmenityUpdateRequest request);

    // ---------------- Dùng bởi module khác (search / interaction / ai) ----------------

    /**
     * Lấy tin đang hiển thị công khai theo id (dùng để module khác xác thực mục tiêu thao tác:
     * bình luận, đánh giá, liên hệ). Ném {@code LISTING_NOT_FOUND} nếu không tồn tại,
     * {@code LISTING_NOT_ACTIVE} nếu tin không public.
     */
    Listing getActiveById(Long id);

    /** Tập trạng thái công khai hiện hành (ủy quyền cho {@link ListingVisibilityService}). */
    Set<ListingStatus> getPublicStatuses();

    /** Tin có đang hiển thị công khai không (canonical §5.2). */
    boolean publiclyVisible(Long listingId);

    /** Tăng/giảm bộ đếm lượt lưu (favorite). {@code delta} có thể âm khi bỏ lưu. */
    void incrementFavoriteCount(Long listingId, int delta);

    /** Tăng bộ đếm lượt liên hệ. */
    void incrementContactCount(Long listingId);

    /**
     * Kết quả sắp xếp ảnh trả về cho client (id + thứ tự + cờ ảnh chính).
     */
    record ListingImageResponseOrder(Long id, Integer displayOrder, Boolean primary) {
    }
}
