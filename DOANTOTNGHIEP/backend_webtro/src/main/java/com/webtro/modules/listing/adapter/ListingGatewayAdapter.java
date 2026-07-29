package com.webtro.modules.listing.adapter;

import com.webtro.constant.ErrorCode;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.catalog.entity.District;
import com.webtro.modules.catalog.entity.Province;
import com.webtro.modules.catalog.entity.Ward;
import com.webtro.modules.catalog.repository.DistrictRepository;
import com.webtro.modules.catalog.repository.ProvinceRepository;
import com.webtro.modules.catalog.repository.WardRepository;
import com.webtro.modules.interaction.spi.ListingGateway;
import com.webtro.modules.listing.entity.Listing;
import com.webtro.modules.listing.repository.ListingImageRepository;
import com.webtro.modules.listing.repository.ListingRepository;
import com.webtro.modules.listing.service.ListingService;
import com.webtro.modules.listing.service.ListingVisibilityService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Adapter hiện thực {@link ListingGateway} (SPI do module {@code interaction} sở hữu) — canonical
 * luật 4. Bọc {@code ListingRepository} + {@code ListingImageRepository} +
 * {@link ListingVisibilityService}, ủy quyền bộ đếm liên hệ cho {@link ListingService}.
 */
@Component
@RequiredArgsConstructor
public class ListingGatewayAdapter implements ListingGateway {

    private final ListingRepository listingRepository;
    private final ListingImageRepository imageRepository;
    private final ProvinceRepository provinceRepository;
    private final DistrictRepository districtRepository;
    private final WardRepository wardRepository;
    private final ListingVisibilityService visibilityService;
    private final ListingService listingService;

    @Override
    @Transactional(readOnly = true)
    public ListingBrief getBrief(Long listingId) {
        return listingRepository.findAliveById(listingId)
                .map(this::toBrief)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LISTING_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ListingBrief> findBrief(Long listingId) {
        return listingRepository.findAliveById(listingId).map(this::toBrief);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ListingBrief> getBriefs(Collection<Long> listingIds) {
        if (listingIds == null || listingIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = new HashSet<>(listingIds);
        Specification<Listing> spec = (root, cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(root.get("id").in(ids));
            ps.add(cb.isNull(root.get("deletedAt")));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Map<Long, ListingBrief> result = new LinkedHashMap<>();
        for (Listing l : listingRepository.findAll(spec)) {
            result.put(l.getId(), toBrief(l));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPubliclyVisible(Long listingId) {
        return listingRepository.findAliveById(listingId)
                .map(visibilityService::publiclyVisible)
                .orElse(false);
    }

    @Override
    @Transactional
    public void setFavoriteCount(Long listingId, int count) {
        listingRepository.findAliveById(listingId).ifPresent(l -> {
            l.setFavoriteCount(Math.max(0, count));
            listingRepository.save(l);
        });
    }

    @Override
    @Transactional
    public void incrementContactCount(Long listingId) {
        listingService.incrementContactCount(listingId);
    }

    @Override
    @Transactional
    public void setCommentCount(Long listingId, int count) {
        listingRepository.findAliveById(listingId).ifPresent(l -> {
            l.setCommentCount(Math.max(0, count));
            listingRepository.save(l);
        });
    }

    @Override
    @Transactional
    public void updateReviewAggregate(Long listingId, BigDecimal averageRating, int reviewCount) {
        listingRepository.findAliveById(listingId).ifPresent(l -> {
            l.setAverageRating(averageRating == null ? BigDecimal.ZERO : averageRating);
            l.setReviewCount(Math.max(0, reviewCount));
            listingRepository.save(l);
        });
    }

    // ==================================================================
    //  Helper
    // ==================================================================

    private ListingBrief toBrief(Listing l) {
        return new ListingBrief(
                l.getId(),
                l.getOwnerId(),
                l.getTitle(),
                l.getSlug(),
                thumbnailOf(l.getId()),
                l.getPrice(),
                l.getArea(),
                shortAddress(l),
                l.getStatus(),
                visibilityService.publiclyVisible(l),
                l.getAverageRating(),
                nz(l.getReviewCount()),
                nz(l.getContactCount()));
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
}
