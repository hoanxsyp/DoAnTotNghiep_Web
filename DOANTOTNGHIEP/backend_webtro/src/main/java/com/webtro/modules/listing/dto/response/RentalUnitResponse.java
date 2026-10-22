package com.webtro.modules.listing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Phòng của chủ trọ (màn "Phòng của tôi"). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentalUnitResponse {
    private Long id;
    private String name;
    private String unitLabel;

    private Long provinceId;
    private String provinceName;
    private Long districtId;
    private String districtName;
    private Long wardId;
    private String wardName;
    private String addressDetail;
    private String fullAddress;

    private String status;
    private String statusLabel;
    private Instant archivedAt;

    private BigDecimal averageRating;
    private Integer reviewCount;
    /** Đã có đánh giá: không đổi được địa chỉ/số phòng (RU-4). */
    private Boolean addressLocked;

    /** Tin "sống" hiện tại của phòng (RU-2: tối đa một); {@code null} nếu không có. */
    private ListingItem liveListing;
    /** Số tin của phòng (không tính tin đã xoá). */
    private Integer listingCount;
    /** Lịch sử tin của phòng, mới nhất trước — chỉ có ở API chi tiết. */
    private List<ListingItem> listings;

    /** Hành động khả dụng để FE chỉ việc hiện nút: RELIST, CREATE_LISTING, EDIT, ARCHIVE, UNARCHIVE. */
    private List<String> availableActions;

    private Instant createdAt;
    private Instant updatedAt;

    /** Tin rút gọn của phòng. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListingItem {
        private Long id;
        private String title;
        private String slug;
        private String status;
        private String statusLabel;
        private String thumbnailUrl;
        private Instant publishedAt;
        private Instant createdAt;
    }
}
