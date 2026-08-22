package com.webtro.modules.listing.mapper;

import com.webtro.common.enums.ListingStatus;
import com.webtro.constant.ConfigKey;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.catalog.entity.Amenity;
import com.webtro.modules.catalog.entity.Category;
import com.webtro.modules.catalog.entity.District;
import com.webtro.modules.catalog.entity.Province;
import com.webtro.modules.catalog.entity.Ward;
import com.webtro.modules.catalog.repository.AmenityRepository;
import com.webtro.modules.catalog.repository.CategoryRepository;
import com.webtro.modules.catalog.repository.WardRepository;
import com.webtro.modules.listing.entity.Listing;
import com.webtro.modules.listing.entity.ListingImage;
import com.webtro.modules.listing.dto.response.LandlordSummaryResponse;
import com.webtro.modules.listing.dto.response.ListingAmenityResponse;
import com.webtro.modules.listing.dto.response.ListingDetailResponse;
import com.webtro.modules.listing.dto.response.ListingImageResponse;
import com.webtro.modules.listing.dto.response.ListingSummaryResponse;
import com.webtro.modules.listing.repository.ListingAmenityRepository;
import com.webtro.modules.listing.repository.ListingImageRepository;
import com.webtro.modules.listing.service.TrustScoreService;
import com.webtro.modules.listing.statemachine.ListingEvent;
import com.webtro.modules.listing.statemachine.ListingStateMachine;
import com.webtro.modules.user.entity.LandlordProfile;
import com.webtro.modules.user.entity.User;
import com.webtro.modules.user.repository.LandlordProfileRepository;
import com.webtro.modules.user.repository.UserRepository;
import com.webtro.util.MaskUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Chuyển đổi entity tin đăng ↔ DTO — nơi DUY NHẤT được phép chạm cả entity và DTO (canonical luật
 * 2, 3). Viết thủ công bằng Builder, không MapStruct.
 *
 * <p>Mapper tự phân giải tên danh mục/địa giới/tiện ích/chủ trọ qua repository của các bảng tra
 * cứu (read-only) để controller và service không phải lo việc ghép dữ liệu hiển thị.
 */
@Component
@RequiredArgsConstructor
public class ListingMapper {

    private final CategoryRepository categoryRepository;
    private final WardRepository wardRepository;
    private final AmenityRepository amenityRepository;
    private final ListingImageRepository listingImageRepository;
    private final ListingAmenityRepository listingAmenityRepository;
    private final UserRepository userRepository;
    private final LandlordProfileRepository landlordProfileRepository;
    private final TrustScoreService trustScoreService;
    private final ListingStateMachine stateMachine;
    private final SystemConfigService config;

    // ==================================================================
    //  Summary (danh sách)
    // ==================================================================

    /**
     * Ánh xạ tin sang bản rút gọn. {@code ownerView = true} bổ sung các trường quản lý tin
     * (trạng thái, lý do từ chối, bộ đếm, hành động khả dụng).
     */
    public ListingSummaryResponse toSummary(Listing l, boolean ownerView) {
        Category category = categoryRepository.findById(l.getCategoryId()).orElse(null);
        ListingSummaryResponse.ListingSummaryResponseBuilder b = ListingSummaryResponse.builder()
                .id(l.getId())
                .slug(l.getSlug())
                .title(l.getTitle())
                .categoryCode(category == null ? null : category.getCode().name())
                .categoryName(category == null ? null : category.getName())
                .price(l.getPrice())
                .area(l.getArea())
                .shortAddress(shortAddress(l))
                .thumbnailUrl(primaryThumbnail(l.getId()))
                .trustScore(toInt(l.getTrustScore()))
                .averageRating(l.getAverageRating())
                .promoted(Boolean.TRUE.equals(l.getIsPromoted()));

        if (ownerView) {
            Long daysRemaining = daysRemaining(l.getExpiredAt());
            b.status(l.getStatus().name())
                    .statusLabel(l.getStatus().getLabel())
                    .rejectReason(l.getRejectReason())
                    .viewCount(l.getViewCount())
                    .favoriteCount(l.getFavoriteCount())
                    .contactCount(l.getContactCount())
                    .publishedAt(l.getPublishedAt())
                    .expiredAt(l.getExpiredAt())
                    .daysRemaining(daysRemaining)
                    .expiringSoon(isExpiringSoon(daysRemaining))
                    .availableActions(availableActions(l));
        }
        return b.build();
    }

    // ==================================================================
    //  Detail (chi tiết)
    // ==================================================================

    /**
     * Ánh xạ tin sang chi tiết đầy đủ.
     *
     * @param viewerAuthenticated người xem đã đăng nhập chưa (quyết định che SĐT + tọa độ)
     * @param privileged          người xem là chủ tin hoặc admin (được xem cả thông tin nhạy cảm)
     */
    public ListingDetailResponse toDetail(Listing l, boolean viewerAuthenticated, boolean privileged) {
        Category category = categoryRepository.findById(l.getCategoryId()).orElse(null);
        LocationParts location = locationOf(l.getWardId());

        boolean revealSensitive = viewerAuthenticated || privileged;
        int trust = toInt(l.getTrustScore());

        return ListingDetailResponse.builder()
                .id(l.getId())
                .slug(l.getSlug())
                .title(l.getTitle())
                .description(l.getDescription())
                .categoryId(l.getCategoryId())
                .categoryCode(category == null ? null : category.getCode().name())
                .categoryName(category == null ? null : category.getName())
                .price(l.getPrice())
                .depositAmount(l.getDepositAmount())
                .electricityPrice(l.getElectricityPrice())
                .waterPrice(l.getWaterPrice())
                .area(l.getArea())
                .pricePerSquareMeter(pricePerSqm(l.getPrice(), l.getArea()))
                .provinceId(location.province() == null ? null : location.province().getId())
                .provinceName(location.province() == null ? null : location.province().getName())
                .districtId(location.district() == null ? null : location.district().getId())
                .districtName(location.district() == null ? null : location.district().getName())
                .wardId(l.getWardId())
                .wardName(location.ward() == null ? null : location.ward().getName())
                .addressDetail(l.getAddressDetail())
                .fullAddress(fullAddress(l, location.province(), location.district(), location.ward()))
                .latitude(revealSensitive ? l.getLatitude() : null)
                .longitude(revealSensitive ? l.getLongitude() : null)
                .roomCount(l.getRoomCount())
                .toiletCount(l.getToiletCount())
                .floorCount(l.getFloorCount())
                .maxOccupants(l.getMaxOccupants())
                .currentOccupants(l.getCurrentOccupants())
                .genderRequirement(l.getGenderRequirement() == null ? null : l.getGenderRequirement().name())
                .petAllowed(l.getPetAllowed())
                .parkingAvailable(l.getParkingAvailable())
                .curfewType(l.getCurfewType() == null ? null : l.getCurfewType().name())
                .furnitureStatus(l.getFurnitureStatus() == null ? null : l.getFurnitureStatus().name())
                .toiletType(l.getToiletType() == null ? null : l.getToiletType().name())
                .availableFrom(l.getAvailableFrom())
                .images(imagesOf(l.getId()))
                .amenities(amenitiesOf(l.getId()))
                .landlord(landlordOf(l.getOwnerId()))
                .contactName(contactName(l))
                .contactPhone(revealSensitive ? contactPhone(l) : MaskUtil.maskPhone(contactPhone(l)))
                .phoneMasked(!revealSensitive)
                .status(l.getStatus().name())
                .statusLabel(l.getStatus().getLabel())
                .trustScore(trust)
                .trustLabel(trustScoreService.labelOf(trust).name())
                .lowTrustWarning(trust < config.getInt(ConfigKey.TRUST_THRESHOLD_RISKY))
                .averageRating(l.getAverageRating())
                .reviewCount(l.getReviewCount())
                .commentCount(l.getCommentCount())
                .viewCount(l.getViewCount())
                .favoriteCount(l.getFavoriteCount())
                .contactCount(l.getContactCount())
                .favoritedByMe(false)
                .reviewedByMe(false)
                .canReview(false)
                .canComment(viewerAuthenticated)
                .promoted(Boolean.TRUE.equals(l.getIsPromoted()))
                .promotedLabel(Boolean.TRUE.equals(l.getIsPromoted()) ? "Tin nổi bật" : null)
                .publishedAt(l.getPublishedAt())
                .expiredAt(l.getExpiredAt())
                .createdAt(l.getCreatedAt())
                .updatedAt(l.getUpdatedAt())
                .build();
    }

    // ==================================================================
    //  Ảnh & tiện ích
    // ==================================================================

    public ListingImageResponse toImageResponse(ListingImage i) {
        return ListingImageResponse.builder()
                .id(i.getId())
                .url(i.getUrl())
                .thumbnailUrl(i.getThumbnailUrl())
                .primary(i.getIsPrimary())
                .displayOrder(i.getDisplayOrder())
                .width(i.getWidth())
                .height(i.getHeight())
                .build();
    }

    public List<ListingImageResponse> imagesOf(Long listingId) {
        return listingImageRepository.findByListingIdAlive(listingId).stream()
                .map(this::toImageResponse)
                .toList();
    }

    public ListingAmenityResponse toAmenityResponse(Amenity a) {
        return ListingAmenityResponse.builder()
                .id(a.getId())
                .code(a.getCode())
                .name(a.getName())
                .group(a.getGroupCode() == null ? null : a.getGroupCode().name())
                .iconUrl(a.getIcon())
                .build();
    }

    public List<ListingAmenityResponse> amenitiesOf(Long listingId) {
        List<Long> ids = listingAmenityRepository.findAmenityIdsByListingIdAlive(listingId);
        if (ids.isEmpty()) {
            return List.of();
        }
        return amenityRepository.findAllById(ids).stream()
                .map(this::toAmenityResponse)
                .toList();
    }

    // ==================================================================
    //  Chủ trọ
    // ==================================================================

    public LandlordSummaryResponse landlordOf(Long ownerId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(ownerId).orElse(null);
        Optional<LandlordProfile> profileOpt = landlordProfileRepository.findByUser_IdAndDeletedAtIsNull(ownerId);
        LandlordProfile profile = profileOpt.orElse(null);

        int trust = profile == null ? config.getInt(ConfigKey.TRUST_BASE_SCORE) : toInt(profile.getTrustScore());
        return LandlordSummaryResponse.builder()
                .id(ownerId)
                .fullName(user == null ? null : user.getFullName())
                .avatarUrl(user == null ? null : user.getAvatarUrl())
                .verified(profile != null
                        && profile.getVerificationStatus() == com.webtro.common.enums.VerificationStatus.VERIFIED)
                .trustScore(trust)
                .trustLabel(trustScoreService.labelOf(trust).name())
                .averageRating(profile == null ? BigDecimal.ZERO : profile.getAverageRating())
                .totalActiveListings(profile == null ? 0 : profile.getTotalActiveListings())
                .chatEnabled(profile == null || Boolean.TRUE.equals(profile.getAllowChat()))
                .memberSince(user == null ? null : user.getCreatedAt())
                .followedByMe(false)
                .build();
    }

    // ==================================================================
    //  Helper
    // ==================================================================

    /** Tên liên hệ ưu tiên hồ sơ chủ trọ (tin đăng không lưu contactName riêng). */
    private String contactName(Listing l) {
        return landlordProfileRepository.findByUser_IdAndDeletedAtIsNull(l.getOwnerId())
                .map(LandlordProfile::getContactName)
                .orElse(null);
    }

    private String contactPhone(Listing l) {
        return landlordProfileRepository.findByUser_IdAndDeletedAtIsNull(l.getOwnerId())
                .map(LandlordProfile::getContactPhone)
                .orElse(null);
    }

    private String primaryThumbnail(Long listingId) {
        return listingImageRepository.findPrimaryByListingIdAlive(listingId)
                .map(i -> i.getThumbnailUrl() != null ? i.getThumbnailUrl() : i.getUrl())
                .orElseGet(() -> listingImageRepository.findByListingIdAlive(listingId).stream()
                        .findFirst()
                        .map(i -> i.getThumbnailUrl() != null ? i.getThumbnailUrl() : i.getUrl())
                        .orElse(null));
    }

    private String shortAddress(Listing l) {
        LocationParts location = locationOf(l.getWardId());
        return joinNonBlank(", ",
                location.ward() == null ? null : location.ward().getName(),
                location.district() == null ? null : location.district().getName(),
                location.province() == null ? null : location.province().getName());
    }

    private LocationParts locationOf(Long wardId) {
        Ward ward = wardId == null ? null : wardRepository.findById(wardId).orElse(null);
        District district = ward == null ? null : ward.getDistrict();
        Province province = district == null ? null : district.getProvince();
        return new LocationParts(province, district, ward);
    }

    private record LocationParts(Province province, District district, Ward ward) {
    }

    private String fullAddress(Listing l, Province province, District district, Ward ward) {
        return joinNonBlank(", ",
                l.getAddressDetail(),
                ward == null ? null : ward.getName(),
                district == null ? null : district.getName(),
                province == null ? null : province.getName());
    }

    private BigDecimal pricePerSqm(BigDecimal price, BigDecimal area) {
        if (price == null || area == null || area.signum() <= 0) {
            return null;
        }
        return price.divide(area, 2, RoundingMode.HALF_UP);
    }

    private Long daysRemaining(Instant expiredAt) {
        if (expiredAt == null) {
            return null;
        }
        long days = Duration.between(Instant.now(), expiredAt).toDays();
        return Math.max(0, days);
    }

    private boolean isExpiringSoon(Long daysRemaining) {
        if (daysRemaining == null) {
            return false;
        }
        List<Integer> reminderDays = config.getIntList(ConfigKey.LISTING_EXPIRY_REMINDER_DAYS);
        int max = reminderDays.stream().mapToInt(Integer::intValue).max().orElse(3);
        return daysRemaining <= max;
    }

    /** Danh sách hành động khả dụng, suy ra từ state machine để FE chỉ việc render. */
    private List<String> availableActions(Listing l) {
        ListingStatus s = l.getStatus();
        List<String> actions = new ArrayList<>();
        if (s != ListingStatus.LOCKED && s != ListingStatus.DELETED) {
            actions.add("EDIT");
        }
        if (stateMachine.canTransition(s, ListingEvent.SUBMIT)) {
            actions.add("SUBMIT");
        }
        if (stateMachine.canTransition(s, ListingEvent.HIDE_BY_OWNER)) {
            actions.add("HIDE");
        }
        if (stateMachine.canTransition(s, ListingEvent.UNHIDE_BY_OWNER)) {
            actions.add("UNHIDE");
        }
        if (stateMachine.canTransition(s, ListingEvent.CLOSE)) {
            actions.add("CLOSE");
        }
        if (stateMachine.canTransition(s, ListingEvent.RENEW)) {
            actions.add("RENEW");
        }
        if (s == ListingStatus.ACTIVE) {
            actions.add("PROMOTE");
        }
        actions.add("VIEW_STATS");
        if (stateMachine.canTransition(s, ListingEvent.SOFT_DELETE)) {
            actions.add("DELETE");
        }
        return actions;
    }

    private static int toInt(BigDecimal v) {
        return v == null ? 0 : v.setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private static String joinNonBlank(String sep, String... parts) {
        return java.util.Arrays.stream(parts)
                .filter(p -> p != null && !p.isBlank())
                .reduce((a, b) -> a + sep + b)
                .orElse(null);
    }

    /** So sánh ảnh theo thứ tự hiển thị (dùng khi cần sắp xếp lại danh sách ảnh trước khi map). */
    public static final Comparator<ListingImage> BY_DISPLAY_ORDER =
            Comparator.comparing(ListingImage::getDisplayOrder,
                    Comparator.nullsLast(Comparator.naturalOrder()));
}
