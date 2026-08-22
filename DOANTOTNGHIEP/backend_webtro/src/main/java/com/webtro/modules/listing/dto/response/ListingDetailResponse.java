package com.webtro.modules.listing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Chi tiết đầy đủ của tin đăng (docs/03 mục 4.4.4, §5.3) — gồm ảnh, tiện ích, chủ trọ.
 *
 * <p>Tọa độ ({@code latitude}/{@code longitude}) và số điện thoại bị che với khách chưa đăng nhập
 * (canonical §8, §3.8). {@code moderationImpact} chỉ có mặt trong response của luồng sửa tin.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListingDetailResponse {

    private Long id;
    private String slug;
    private String title;
    private String description;

    private Long categoryId;
    private String categoryCode;
    private String categoryName;

    private BigDecimal price;
    private BigDecimal depositAmount;
    private BigDecimal electricityPrice;
    private BigDecimal waterPrice;
    private BigDecimal area;
    private BigDecimal pricePerSquareMeter;

    private Long provinceId;
    private String provinceName;
    private Long districtId;
    private String districtName;
    private Long wardId;
    private String wardName;
    private String addressDetail;
    private String fullAddress;
    private BigDecimal latitude;
    private BigDecimal longitude;

    private Integer roomCount;
    private Integer toiletCount;
    private Integer floorCount;
    private Integer maxOccupants;
    private Integer currentOccupants;
    private String genderRequirement;
    private Boolean petAllowed;
    private Boolean parkingAvailable;
    private String curfewType;
    private String furnitureStatus;
    private String toiletType;
    private LocalDate availableFrom;

    private List<ListingImageResponse> images;
    private List<ListingAmenityResponse> amenities;
    private LandlordSummaryResponse landlord;

    private String contactName;
    private String contactPhone;
    private Boolean phoneMasked;

    private String status;
    private String statusLabel;
    private Integer trustScore;
    private String trustLabel;
    private Boolean lowTrustWarning;
    private BigDecimal averageRating;
    private Integer reviewCount;
    private Integer commentCount;
    private Integer viewCount;
    private Integer favoriteCount;
    private Integer contactCount;

    private Boolean favoritedByMe;
    private Boolean reviewedByMe;
    private Boolean canReview;
    private Boolean canComment;

    private Boolean promoted;
    private String promotedLabel;

    private Instant publishedAt;
    private Instant expiredAt;
    private Instant createdAt;
    private Instant updatedAt;

    /** Chỉ xuất hiện ở response sửa tin (LIST-03) khi có thay đổi ảnh hưởng kiểm duyệt. */
    private ModerationImpactResponse moderationImpact;
}
