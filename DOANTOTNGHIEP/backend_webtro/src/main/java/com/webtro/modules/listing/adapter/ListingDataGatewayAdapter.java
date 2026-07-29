package com.webtro.modules.listing.adapter;

import com.webtro.common.enums.ListingStatus;
import com.webtro.modules.ai.spi.ListingDataGateway;
import com.webtro.modules.catalog.entity.Category;
import com.webtro.modules.catalog.entity.District;
import com.webtro.modules.catalog.entity.Province;
import com.webtro.modules.catalog.entity.Ward;
import com.webtro.modules.catalog.repository.CategoryRepository;
import com.webtro.modules.catalog.repository.DistrictRepository;
import com.webtro.modules.catalog.repository.ProvinceRepository;
import com.webtro.modules.catalog.repository.WardRepository;
import com.webtro.modules.listing.entity.Listing;
import com.webtro.modules.listing.repository.ListingAmenityRepository;
import com.webtro.modules.listing.repository.ListingImageRepository;
import com.webtro.modules.listing.repository.ListingRepository;
import com.webtro.modules.listing.service.ListingVisibilityService;
import com.webtro.modules.listing.service.TrustScoreService;
import com.webtro.modules.listing.statemachine.ListingEvent;
import com.webtro.modules.listing.statemachine.ListingStateMachine;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Adapter hiện thực {@link ListingDataGateway} (SPI do module {@code ai} sở hữu) — canonical luật 4.
 *
 * <p>Bọc {@code ListingRepository} + các repository tra cứu của module listing; mọi truy vấn bỏ qua
 * tin đã xóa mềm và dùng {@link ListingVisibilityService#publicStatuses()} thay vì so cứng ACTIVE.
 * Thao tác đổi trạng thái đi qua {@link ListingStateMachine}; tính lại điểm uy tín qua
 * {@link TrustScoreService}.
 */
@Component
@RequiredArgsConstructor
public class ListingDataGatewayAdapter implements ListingDataGateway {

    private final ListingRepository listingRepository;
    private final ListingImageRepository imageRepository;
    private final ListingAmenityRepository amenityLinkRepository;
    private final CategoryRepository categoryRepository;
    private final ProvinceRepository provinceRepository;
    private final DistrictRepository districtRepository;
    private final WardRepository wardRepository;
    private final ListingVisibilityService visibilityService;
    private final ListingStateMachine stateMachine;
    private final TrustScoreService trustScoreService;

    // ==================================================================
    //  Đọc thuộc tính
    // ==================================================================

    @Override
    @Transactional(readOnly = true)
    public Optional<ListingAttr> getAttribute(Long listingId) {
        return listingRepository.findAliveById(listingId)
                .map(l -> toAttr(l, categoryRepository.findById(l.getCategoryId()).orElse(null)));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ListingAttr> getAttributes(Collection<Long> listingIds) {
        if (listingIds == null || listingIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = new java.util.HashSet<>(listingIds);
        List<Listing> listings = listingRepository.findAll(aliveByIds(ids));
        Map<Long, Category> categories = categoriesOf(listings);
        Map<Long, ListingAttr> result = new LinkedHashMap<>();
        for (Listing l : listings) {
            result.put(l.getId(), toAttr(l, categories.get(l.getCategoryId())));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ListingAttr> findCandidates(CandidateQuery query) {
        if (query.limit() <= 0) {
            return List.of();
        }
        Specification<Listing> spec = (root, cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.isNull(root.get("deletedAt")));
            ps.add(root.get("status").in(visibilityService.publicStatuses()));
            if (query.provinceId() != null) {
                ps.add(cb.equal(root.get("provinceId"), query.provinceId()));
            }
            if (query.districtId() != null) {
                ps.add(cb.equal(root.get("districtId"), query.districtId()));
            }
            if (query.wardId() != null) {
                ps.add(cb.equal(root.get("wardId"), query.wardId()));
            }
            if (query.categoryId() != null) {
                ps.add(cb.equal(root.get("categoryId"), query.categoryId()));
            }
            if (query.priceFrom() != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("price"), query.priceFrom()));
            }
            if (query.priceTo() != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("price"), query.priceTo()));
            }
            if (query.areaFrom() != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("area"), query.areaFrom()));
            }
            if (query.areaTo() != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("area"), query.areaTo()));
            }
            if (query.excludeListingIds() != null && !query.excludeListingIds().isEmpty()) {
                ps.add(cb.not(root.get("id").in(query.excludeListingIds())));
            }
            if (query.excludeOwnerId() != null) {
                ps.add(cb.notEqual(root.get("ownerId"), query.excludeOwnerId()));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        PageRequest page = query.newestFirst()
                ? PageRequest.of(0, query.limit(), Sort.by(Sort.Direction.DESC, "publishedAt"))
                : PageRequest.of(0, query.limit());
        List<Listing> listings = listingRepository.findAll(spec, page).getContent();
        Map<Long, Category> categories = categoriesOf(listings);
        return listings.stream()
                .map(l -> toAttr(l, categories.get(l.getCategoryId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ComparableListing> findComparables(ComparableQuery query) {
        if (query.limit() <= 0) {
            return List.of();
        }
        Specification<Listing> spec = (root, cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.isNull(root.get("deletedAt")));
            ps.add(root.get("status").in(List.of(ListingStatus.ACTIVE, ListingStatus.CLOSED)));
            // Phạm vi địa lý theo scope.
            if (query.scope() != null) {
                switch (query.scope()) {
                    case WARD -> {
                        if (query.wardId() != null) {
                            ps.add(cb.equal(root.get("wardId"), query.wardId()));
                        }
                    }
                    case DISTRICT -> {
                        if (query.districtId() != null) {
                            ps.add(cb.equal(root.get("districtId"), query.districtId()));
                        }
                    }
                    case PROVINCE -> {
                        if (query.provinceId() != null) {
                            ps.add(cb.equal(root.get("provinceId"), query.provinceId()));
                        }
                    }
                    default -> {
                    }
                }
            }
            if (query.categoryId() != null) {
                ps.add(cb.equal(root.get("categoryId"), query.categoryId()));
            }
            if (query.areaMin() != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("area"), query.areaMin()));
            }
            if (query.areaMax() != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("area"), query.areaMax()));
            }
            if (query.publishedAfter() != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("publishedAt"), query.publishedAfter()));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        PageRequest page = PageRequest.of(0, query.limit(),
                Sort.by(Sort.Direction.DESC, "publishedAt"));
        return listingRepository.findAll(spec, page).getContent().stream()
                .map(this::toComparable)
                .toList();
    }

    // ==================================================================
    //  Ghi
    // ==================================================================

    @Override
    @Transactional
    public void applySentimentCounts(Long listingId, int positiveCount, int negativeCount) {
        listingRepository.findAliveById(listingId).ifPresent(l -> {
            l.setPositiveCommentCount(Math.max(0, positiveCount));
            l.setNegativeCommentCount(Math.max(0, negativeCount));
            listingRepository.save(l);
            // Cập nhật điểm uy tín tin (đọc lại hai bộ đếm vừa ghi) — canonical §5.8.
            trustScoreService.recalculateAndSaveListing(listingId);
        });
    }

    @Override
    @Transactional
    public boolean flagNeedReview(Long listingId, String reason) {
        Optional<Listing> opt = listingRepository.findAliveById(listingId);
        if (opt.isEmpty()) {
            return false;
        }
        Listing l = opt.get();
        // AI chỉ gắn cờ khi tin đang ACTIVE; không ở trạng thái cho phép thì bỏ qua (không lỗi).
        if (!stateMachine.canTransition(l.getStatus(), ListingEvent.FLAG_NEED_REVIEW)) {
            return false;
        }
        l.setStatus(stateMachine.transition(l.getStatus(), ListingEvent.FLAG_NEED_REVIEW));
        l.setNeedReviewCount(nz(l.getNeedReviewCount()) + 1);
        l.setLastNeedReviewAt(Instant.now());
        listingRepository.save(l);
        return true;
    }

    @Override
    @Transactional
    public void markPriceDeviation(Long listingId, boolean deviationFlagged, Long predictionHistoryId) {
        listingRepository.findAliveById(listingId).ifPresent(l -> {
            l.setPriceDeviationFlag(deviationFlagged);
            if (predictionHistoryId != null) {
                l.setPricePredictionId(predictionHistoryId);
            }
            listingRepository.save(l);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPriorWarning(Long listingId) {
        return listingRepository.findAliveById(listingId)
                .map(l -> nz(l.getNeedReviewCount()) > 0)
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> ownerOf(Long listingId) {
        return listingRepository.findAliveById(listingId).map(Listing::getOwnerId);
    }

    // ==================================================================
    //  Helper
    // ==================================================================

    private ListingAttr toAttr(Listing l, Category category) {
        return new ListingAttr(
                l.getId(),
                l.getOwnerId(),
                l.getProvinceId(),
                l.getDistrictId(),
                l.getWardId(),
                l.getCategoryId(),
                category == null ? null : category.getCode(),
                category == null ? null : category.getName(),
                l.getPrice(),
                l.getArea(),
                l.getMaxOccupants(),
                l.getGenderRequirement(),
                l.getFurnitureStatus(),
                l.getToiletType(),
                l.getCurfewType(),
                Boolean.TRUE.equals(l.getParkingAvailable()),
                amenityLinkRepository.findAmenityIdsByListingIdAlive(l.getId()),
                intOf(l.getTrustScore()),
                l.getAverageRating(),
                l.getPublishedAt(),
                Boolean.TRUE.equals(l.getIsPromoted()),
                l.getStatus(),
                visibilityService.publiclyVisible(l),
                l.getSlug(),
                l.getTitle(),
                shortAddress(l),
                thumbnailOf(l.getId()));
    }

    private ComparableListing toComparable(Listing l) {
        return new ComparableListing(
                l.getId(),
                l.getWardId(),
                l.getDistrictId(),
                l.getProvinceId(),
                l.getPrice(),
                l.getArea(),
                l.getFurnitureStatus(),
                l.getToiletType(),
                l.getCurfewType(),
                Boolean.TRUE.equals(l.getParkingAvailable()));
    }

    private Specification<Listing> aliveByIds(Set<Long> ids) {
        return (root, cq, cb) -> cb.and(
                root.get("id").in(ids),
                cb.isNull(root.get("deletedAt")));
    }

    private Map<Long, Category> categoriesOf(List<Listing> listings) {
        Set<Long> categoryIds = listings.stream()
                .map(Listing::getCategoryId)
                .collect(Collectors.toSet());
        if (categoryIds.isEmpty()) {
            return Map.of();
        }
        return categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
    }

    private String thumbnailOf(Long listingId) {
        return imageRepository.findPrimaryByListingIdAlive(listingId)
                .map(i -> i.getThumbnailUrl() != null ? i.getThumbnailUrl() : i.getUrl())
                .orElseGet(() -> imageRepository.findByListingIdAlive(listingId).stream()
                        .findFirst()
                        .map(i -> i.getThumbnailUrl() != null ? i.getThumbnailUrl() : i.getUrl())
                        .orElse(null));
    }

    private String shortAddress(Listing l) {
        String ward = wardRepository.findById(l.getWardId()).map(Ward::getName).orElse(null);
        String district = districtRepository.findById(l.getDistrictId()).map(District::getName).orElse(null);
        String province = provinceRepository.findById(l.getProvinceId()).map(Province::getName).orElse(null);
        return joinNonBlank(ward, district, province);
    }

    private static String joinNonBlank(String... parts) {
        return java.util.Arrays.stream(parts)
                .filter(p -> p != null && !p.isBlank())
                .reduce((a, b) -> a + ", " + b)
                .orElse(null);
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static int intOf(BigDecimal v) {
        return v == null ? 0 : v.setScale(0, RoundingMode.HALF_UP).intValue();
    }
}
