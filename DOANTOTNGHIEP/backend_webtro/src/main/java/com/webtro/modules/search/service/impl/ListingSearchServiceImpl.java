package com.webtro.modules.search.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webtro.common.PageResponse;
import com.webtro.common.event.SearchPerformedEvent;
import com.webtro.common.enums.ListingStatus;
import com.webtro.constant.ConfigKey;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessException;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.catalog.entity.District;
import com.webtro.modules.catalog.entity.Province;
import com.webtro.modules.catalog.entity.Ward;
import com.webtro.modules.catalog.repository.WardRepository;
import com.webtro.modules.listing.dto.response.ListingSummaryResponse;
import com.webtro.modules.listing.entity.Listing;
import com.webtro.modules.listing.mapper.ListingMapper;
import com.webtro.modules.listing.repository.ListingRepository;
import com.webtro.modules.listing.service.ListingVisibilityService;
import com.webtro.modules.search.dto.request.ListingSearchRequest;
import com.webtro.modules.search.dto.response.RelatedListingItem;
import com.webtro.modules.search.dto.response.RelatedListingsResponse;
import com.webtro.modules.search.service.ListingSearchService;
import com.webtro.modules.search.specification.ListingSortOption;
import com.webtro.modules.search.specification.ListingSpecifications;
import com.webtro.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Hiện thực nghiệp vụ tìm kiếm &amp; tin liên quan (module SEARCH).
 *
 * <p>Kiến trúc: service SỞ HỮU truy vấn (dựng {@link ListingSpecifications} + gọi
 * {@link ListingRepository} — {@code JpaSpecificationExecutor}), nhưng ỦY THÁC việc dựng DTO tóm
 * tắt cho {@link ListingMapper} của module listing (tái dùng, không tạo lại {@code
 * ListingSummaryResponse}). Tập trạng thái public lấy từ {@link ListingVisibilityService}. Lịch sử
 * tìm kiếm ghi phi đồng bộ qua {@link SearchPerformedEvent}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListingSearchServiceImpl implements ListingSearchService {

    /** Ký tự/chuỗi nguy hiểm không cho phép trong từ khóa (docs/03 mục 4.4.1 quy tắc 2). */
    private static final Pattern DANGEROUS_KEYWORD = Pattern.compile("[<>;]|--|/\\*");

    private static final int MAX_PAGE_SIZE = 100;
    private static final int RELATED_MAX_SIZE = 24;
    private static final int RELATED_CANDIDATE_CAP = 60;

    private final ListingRepository listingRepository;
    private final ListingVisibilityService listingVisibilityService;
    private final ListingMapper listingMapper;
    private final SystemConfigService systemConfigService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final WardRepository wardRepository;

    // ---------------------------------------------------------------------
    //  SRCH-01..08 — tìm kiếm & lọc
    // ---------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ListingSummaryResponse> search(ListingSearchRequest request,
                                                       Pageable pageable,
                                                       CustomUserDetails principal) {
        ListingSortOption sort = validate(request);
        Instant now = Instant.now();
        Set<ListingStatus> publicStatuses = listingVisibilityService.publicStatuses();

        Specification<Listing> spec = buildSpecification(request, publicStatuses, now, sort);
        Pageable effective = sanitizePageable(pageable);

        Page<Listing> page = listingRepository.findAll(spec, effective);

        // Kết quả tìm kiếm CHỈ gồm tin public (đã lọc theo publicStatuses), nên luôn ở góc nhìn
        // khách (ownerView = false) — không lộ thông tin chỉ dành cho chủ tin.
        PageResponse<ListingSummaryResponse> response =
                PageResponse.from(page, listing -> listingMapper.toSummary(listing, false));

        // Lưu lịch sử tìm kiếm CHỈ khi đã đăng nhập, phi đồng bộ, không chặn response ([§3.7]).
        if (principal != null) {
            publishSearchHistory(principal.getId(), request, page.getTotalElements());
        }
        return response;
    }

    /**
     * Kiểm tra các ràng buộc phụ thuộc lẫn nhau, đọc ngưỡng từ {@link SystemConfigService}.
     * Ném {@link BusinessException} với đúng {@link ErrorCode} khi vi phạm.
     */
    private ListingSortOption validate(ListingSearchRequest request) {
        // Khoảng giá / diện tích.
        if (request.getPriceFrom() != null && request.getPriceTo() != null
                && request.getPriceFrom().compareTo(request.getPriceTo()) > 0) {
            throw new BusinessException(ErrorCode.PRICE_RANGE_INVALID);
        }
        if (request.getAreaFrom() != null && request.getAreaTo() != null
                && request.getAreaFrom().compareTo(request.getAreaTo()) > 0) {
            throw new BusinessException(ErrorCode.AREA_RANGE_INVALID);
        }

        // Từ khóa: độ dài theo config + ký tự nguy hiểm.
        String keyword = request.getKeyword();
        if (keyword != null && !keyword.isBlank()) {
            int maxLength = systemConfigService.getInt(ConfigKey.SEARCH_KEYWORD_MAX_LENGTH);
            if (keyword.trim().length() > maxLength) {
                throw new BusinessException(ErrorCode.KEYWORD_TOO_LONG);
            }
            if (DANGEROUS_KEYWORD.matcher(keyword).find()) {
                throw new BusinessException(ErrorCode.KEYWORD_INVALID_CHARACTER);
            }
        }

        // Phân cấp địa giới: district cần province, ward cần district.
        if (request.getDistrictId() != null && request.getProvinceId() == null) {
            throw new BusinessException(ErrorCode.FILTER_COMBINATION_INVALID);
        }
        if (request.getWardId() != null && request.getDistrictId() == null) {
            throw new BusinessException(ErrorCode.FILTER_COMBINATION_INVALID);
        }

        // Số lượng tiện ích theo config.
        if (request.getAmenityIds() != null) {
            int maxCount = systemConfigService.getInt(ConfigKey.SEARCH_AMENITY_FILTER_MAX_COUNT);
            if (request.getAmenityIds().size() > maxCount) {
                throw new BusinessException(ErrorCode.TOO_MANY_AMENITY_FILTERS);
            }
        }

        // Sắp xếp whitelist.
        return ListingSortOption.from(request.getSort())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_SORT_FIELD));
    }

    private Specification<Listing> buildSpecification(ListingSearchRequest r,
                                                      Set<ListingStatus> publicStatuses,
                                                      Instant now,
                                                      ListingSortOption sort) {
        List<Specification<Listing>> specs = new ArrayList<>();
        // Khả kiến công khai (bắt buộc).
        specs.add(ListingSpecifications.alive());
        specs.add(ListingSpecifications.statusIn(publicStatuses));
        specs.add(ListingSpecifications.notExpired(now));

        // Bộ lọc người dùng (mỗi phương thức tự trả null nếu tham số vắng).
        addIfPresent(specs, ListingSpecifications.keyword(r.getKeyword()));
        addIfPresent(specs, ListingSpecifications.provinceId(r.getProvinceId()));
        addIfPresent(specs, ListingSpecifications.districtId(r.getDistrictId()));
        addIfPresent(specs, ListingSpecifications.wardId(r.getWardId()));
        addIfPresent(specs, ListingSpecifications.categoryId(r.getCategoryId()));
        addIfPresent(specs, ListingSpecifications.categoryCode(r.getCategoryCode()));
        addIfPresent(specs, ListingSpecifications.priceFrom(r.getPriceFrom()));
        addIfPresent(specs, ListingSpecifications.priceTo(r.getPriceTo()));
        addIfPresent(specs, ListingSpecifications.areaFrom(r.getAreaFrom()));
        addIfPresent(specs, ListingSpecifications.areaTo(r.getAreaTo()));
        addIfPresent(specs, ListingSpecifications.depositMax(r.getDepositMax()));
        addIfPresent(specs, ListingSpecifications.maxOccupantsAtLeast(r.getMaxOccupants()));
        addIfPresent(specs, ListingSpecifications.roomCountAtLeast(r.getRoomCountFrom()));
        addIfPresent(specs, ListingSpecifications.toiletCountAtLeast(r.getToiletCountFrom()));
        addIfPresent(specs, ListingSpecifications.genderRequirement(r.getGenderRequirement()));
        addIfPresent(specs, ListingSpecifications.furnitureStatus(r.getFurnitureStatus()));
        addIfPresent(specs, ListingSpecifications.curfewType(r.getCurfewType()));
        addIfPresent(specs, ListingSpecifications.toiletType(r.getToiletType()));
        addIfPresent(specs, ListingSpecifications.petAllowed(r.getPetAllowed()));
        addIfPresent(specs, ListingSpecifications.parkingAvailable(r.getParkingAvailable()));
        addIfPresent(specs, ListingSpecifications.availableFromBefore(r.getAvailableFrom()));
        addIfPresent(specs, ListingSpecifications.minTrustScore(r.getMinTrustScore()));
        addIfPresent(specs, ListingSpecifications.hasAllAmenityIds(r.getAmenityIds()));
        addIfPresent(specs, ListingSpecifications.hasAmenityCode("BALCONY", r.getHasBalcony()));
        addIfPresent(specs, ListingSpecifications.hasAmenityCode("AIR_CONDITIONER", r.getHasAirConditioner()));
        addIfPresent(specs, ListingSpecifications.hasAmenityCode("WASHING_MACHINE", r.getHasWashingMachine()));
        addIfPresent(specs, ListingSpecifications.hasAmenityCode("ELEVATOR", r.getHasElevator()));
        addIfPresent(specs, ListingSpecifications.withinBoundingBox(
                r.getLatitude(), r.getLongitude(), r.getRadiusKm()));

        // Sắp xếp (promoted còn hạn luôn đầu, rồi tới tiêu chí người dùng).
        specs.add(ListingSpecifications.orderBy(sort, now));
        return Specification.allOf(specs);
    }

    private void addIfPresent(List<Specification<Listing>> specs, Specification<Listing> spec) {
        if (spec != null) {
            specs.add(spec);
        }
    }

    /** Giữ page/size, ép size &le; 100, BỎ sort của client (thứ tự do specification quyết định). */
    private Pageable sanitizePageable(Pageable pageable) {
        int page = Math.max(pageable.getPageNumber(), 0);
        int size = pageable.getPageSize();
        if (size <= 0) {
            size = 20;
        }
        size = Math.min(size, MAX_PAGE_SIZE);
        return PageRequest.of(page, size);
    }

    private void publishSearchHistory(Long userId, ListingSearchRequest request, long resultCount) {
        String criteriaJson;
        try {
            criteriaJson = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            log.warn("Không serialize được bộ lọc tìm kiếm cho user {}: {}", userId, e.getMessage());
            criteriaJson = "{}";
        }
        String keyword = request.getKeyword() == null ? null : request.getKeyword().trim();
        eventPublisher.publishEvent(
                new SearchPerformedEvent(userId, keyword, criteriaJson, resultCount, null));
    }

    // ---------------------------------------------------------------------
    //  Tin công khai của một chủ trọ (trang hồ sơ chủ trọ)
    // ---------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ListingSummaryResponse> listByOwner(Long ownerId, Pageable pageable) {
        Instant now = Instant.now();
        Set<ListingStatus> publicStatuses = listingVisibilityService.publicStatuses();

        List<Specification<Listing>> specs = new ArrayList<>();
        specs.add(ListingSpecifications.alive());
        specs.add(ListingSpecifications.statusIn(publicStatuses));
        specs.add(ListingSpecifications.notExpired(now));
        specs.add(ListingSpecifications.ownerId(ownerId));
        specs.add(ListingSpecifications.orderBy(ListingSortOption.NEWEST, now));

        Page<Listing> page = listingRepository.findAll(Specification.allOf(specs), sanitizePageable(pageable));
        // Chỉ tin public → luôn ở góc nhìn khách (ownerView = false).
        return PageResponse.from(page, listing -> listingMapper.toSummary(listing, false));
    }

    // ---------------------------------------------------------------------
    //  SRCH-09 — tin liên quan
    // ---------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public RelatedListingsResponse related(Long listingId, Integer size, CustomUserDetails principal) {
        Listing base = listingRepository.findAliveById(listingId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LISTING_NOT_FOUND));

        int resolvedSize = resolveRelatedSize(size);
        Instant now = Instant.now();
        Set<ListingStatus> publicStatuses = listingVisibilityService.publicStatuses();

        // Ứng viên: cùng tỉnh, còn hạn, public, khác chính nó, giá trong dải +-30%.
        BigDecimal basePrice = base.getPrice();
        BigDecimal priceLow = scale(basePrice, 0.70);
        BigDecimal priceHigh = scale(basePrice, 1.30);

        List<Specification<Listing>> specs = new ArrayList<>();
        specs.add(ListingSpecifications.alive());
        specs.add(ListingSpecifications.statusIn(publicStatuses));
        specs.add(ListingSpecifications.notExpired(now));
        specs.add(ListingSpecifications.idNot(base.getId()));
        LocationIds baseLocation = locationOf(base.getWardId());
        addIfPresent(specs, ListingSpecifications.provinceId(baseLocation.provinceId()));
        addIfPresent(specs, ListingSpecifications.priceFrom(priceLow));
        addIfPresent(specs, ListingSpecifications.priceTo(priceHigh));
        specs.add(ListingSpecifications.orderBy(ListingSortOption.TRUST_DESC, now));

        int candidateLimit = Math.min(resolvedSize * 5, RELATED_CANDIDATE_CAP);
        Page<Listing> candidates = listingRepository.findAll(
                Specification.allOf(specs), PageRequest.of(0, candidateLimit));

        List<RelatedListingItem> items = candidates.getContent().stream()
                .map(candidate -> toRelatedItem(base, candidate))
                .sorted(Comparator.comparingDouble(RelatedListingItem::getMatchScore).reversed())
                .limit(resolvedSize)
                .toList();

        return RelatedListingsResponse.builder()
                .source("SIMILAR_LISTING")
                .items(items)
                .build();
    }

    private int resolveRelatedSize(Integer requested) {
        int fallback = systemConfigService.getInt(ConfigKey.AI_RECOMMENDATION_SIZE);
        int size = requested != null ? requested : fallback;
        return Math.max(1, Math.min(size, RELATED_MAX_SIZE));
    }

    /**
     * Tính điểm &amp; lý do khớp (nội dung). Trọng số: cùng quận/huyện 0.40, cùng loại 0.35, giá
     * lệch dưới 10% 0.25 (dưới 20% 0.15), diện tích tương đương 0.05. Trần 1.0.
     */
    private RelatedListingItem toRelatedItem(Listing base, Listing candidate) {
        double score = 0.0;
        List<String> reasons = new ArrayList<>();

        LocationIds baseLocation = locationOf(base.getWardId());
        LocationIds candidateLocation = locationOf(candidate.getWardId());
        if (baseLocation.districtId() != null && baseLocation.districtId().equals(candidateLocation.districtId())) {
            score += 0.40;
            reasons.add("Cùng quận/huyện");
        } else {
            score += 0.10;
            reasons.add("Cùng tỉnh/thành");
        }

        if (base.getCategoryId() != null && base.getCategoryId().equals(candidate.getCategoryId())) {
            score += 0.35;
            reasons.add("Cùng loại tin");
        }

        double priceRatio = priceDiffRatio(base.getPrice(), candidate.getPrice());
        if (priceRatio <= 0.10) {
            score += 0.25;
            reasons.add("Giá chênh lệch dưới 10%");
        } else if (priceRatio <= 0.20) {
            score += 0.15;
            reasons.add("Giá tương đương");
        } else {
            score += 0.05;
        }

        if (areaDiffRatio(base.getArea(), candidate.getArea()) <= 0.20) {
            score += 0.05;
            reasons.add("Diện tích tương đương");
        }

        double matchScore = Math.min(1.0, Math.round(score * 100.0) / 100.0);
        return RelatedListingItem.builder()
                .listing(listingMapper.toSummary(candidate, false))
                .matchScore(matchScore)
                .matchReasons(reasons)
                .build();
    }

    private double priceDiffRatio(BigDecimal base, BigDecimal other) {
        if (base == null || other == null || base.signum() <= 0) {
            return 1.0;
        }
        return other.subtract(base).abs()
                .divide(base, 4, RoundingMode.HALF_UP).doubleValue();
    }

    private double areaDiffRatio(BigDecimal base, BigDecimal other) {
        if (base == null || other == null || base.signum() <= 0) {
            return 1.0;
        }
        return other.subtract(base).abs()
                .divide(base, 4, RoundingMode.HALF_UP).doubleValue();
    }

    private BigDecimal scale(BigDecimal value, double factor) {
        if (value == null) {
            return null;
        }
        return value.multiply(BigDecimal.valueOf(factor));
    }

    private LocationIds locationOf(Long wardId) {
        Ward ward = wardId == null ? null : wardRepository.findById(wardId).orElse(null);
        District district = ward == null ? null : ward.getDistrict();
        Province province = district == null ? null : district.getProvince();
        return new LocationIds(
                province == null ? null : province.getId(),
                district == null ? null : district.getId());
    }

    private record LocationIds(Long provinceId, Long districtId) {
    }
}
