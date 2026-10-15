package com.webtro.modules.ai.spi;

import com.webtro.common.enums.CategoryCode;
import com.webtro.common.enums.CurfewType;
import com.webtro.common.enums.FurnitureStatus;
import com.webtro.common.enums.GenderRequirement;
import com.webtro.common.enums.ListingStatus;
import com.webtro.common.enums.ToiletType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cổng (SPI) mà module {@code ai} cần từ module {@code listing} — canonical luật 4. Module
 * {@code listing} phải cung cấp một {@code @Component} hiện thực interface này (bọc
 * {@code ListingRepository} + {@code ListingAmenityRepository} + {@code ListingImageRepository} +
 * {@code ListingStateMachine} + {@code ListingVisibilityService}).
 *
 * <p>Mọi phương thức đọc phải bỏ qua tin đã xóa mềm ({@code deleted_at IS NULL}); phần trả về tin
 * công khai phải dùng {@code ListingVisibilityService.publicStatuses()} (canonical mục 5.2), KHÔNG
 * so cứng {@code status = ACTIVE}.
 */
public interface ListingDataGateway {

    /**
     * Thuộc tính tin phục vụ chấm điểm gợi ý và hiển thị thẻ tin. Gộp đủ trường 9 số hạng
     * (canonical mục 10.2) + trường hiển thị.
     */
    record ListingAttr(
            Long id,
            Long ownerId,
            Long provinceId,
            Long districtId,
            Long wardId,
            Long categoryId,
            CategoryCode categoryCode,
            String categoryName,
            BigDecimal price,
            BigDecimal area,
            Integer maxOccupants,
            GenderRequirement genderRequirement,
            FurnitureStatus furnitureStatus,
            ToiletType toiletType,
            CurfewType curfewType,
            boolean parkingAvailable,
            List<Long> amenityIds,
            BigDecimal averageRating,
            Instant publishedAt,
            boolean promoted,
            ListingStatus status,
            boolean publiclyVisible,
            String slug,
            String title,
            String shortAddress,
            String thumbnailUrl) {
    }

    /** Một tin so sánh phục vụ ước lượng giá (chỉ trường cần thiết). */
    record ComparableListing(
            Long id,
            Long wardId,
            Long districtId,
            Long provinceId,
            BigDecimal price,
            BigDecimal area,
            FurnitureStatus furnitureStatus,
            ToiletType toiletType,
            CurfewType curfewType,
            boolean parkingAvailable) {
    }

    /** Bộ tiêu chí lấy ứng viên gợi ý (đã chuẩn hóa từ hồ sơ/cold-start). */
    record CandidateQuery(
            Long provinceId,
            Long districtId,
            Long wardId,
            Long categoryId,
            BigDecimal priceFrom,
            BigDecimal priceTo,
            BigDecimal areaFrom,
            BigDecimal areaTo,
            Collection<Long> excludeListingIds,
            Long excludeOwnerId,
            boolean newestFirst,
            int limit) {
    }

    /** Bộ tiêu chí lấy tin so sánh cho ước lượng giá theo một phạm vi địa lý. */
    record ComparableQuery(
            Long categoryId,
            Long provinceId,
            Long districtId,
            Long wardId,
            com.webtro.common.enums.AdministrativeUnitType scope,
            BigDecimal areaMin,
            BigDecimal areaMax,
            Instant publishedAfter,
            int limit) {
    }

    /** Thuộc tính một tin (bỏ qua đã xóa mềm); rỗng nếu không tồn tại. */
    Optional<ListingAttr> getAttribute(Long listingId);

    /** Nạp hàng loạt thuộc tính tin (tránh N+1 khi dựng hồ sơ hành vi). */
    Map<Long, ListingAttr> getAttributes(Collection<Long> listingIds);

    /**
     * Lấy ứng viên gợi ý là tin công khai khớp tiêu chí, loại các id/owner bị loại, giới hạn số
     * lượng. Sắp theo {@code publishedAt desc} nếu {@code newestFirst}.
     */
    List<ListingAttr> findCandidates(CandidateQuery query);

    /** Lấy tin so sánh cho ước lượng giá theo phạm vi ({@code ACTIVE}/{@code CLOSED} trong cửa sổ). */
    List<ComparableListing> findComparables(ComparableQuery query);

    /**
     * Ghi cờ lệch giá lên tin ({@code price_deviation_flag}) + gắn {@code price_prediction_id} bản
     * mới nhất. TUYỆT ĐỐI không đổi trạng thái/không chặn (canonical mục 10.4 bước 6).
     */
    void markPriceDeviation(Long listingId, boolean deviationFlagged, Long predictionHistoryId);

    /** Chủ tin. */
    Optional<Long> ownerOf(Long listingId);
}
