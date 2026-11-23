package com.webtro.modules.listing.service.impl;

import com.webtro.common.PageResponse;
import com.webtro.common.enums.ListingStatus;
import com.webtro.common.enums.RentalUnitStatus;
import com.webtro.constant.ErrorCode;
import com.webtro.constant.RoleCode;
import com.webtro.exception.ConflictException;
import com.webtro.exception.ForbiddenException;
import com.webtro.modules.catalog.entity.District;
import com.webtro.modules.catalog.entity.Province;
import com.webtro.modules.catalog.entity.Ward;
import com.webtro.modules.catalog.repository.WardRepository;
import com.webtro.modules.listing.dto.request.RentalUnitRequest;
import com.webtro.modules.listing.dto.response.ListingCreateResponse;
import com.webtro.modules.listing.dto.response.RentalUnitDuplicateCheckResponse;
import com.webtro.modules.listing.dto.response.RentalUnitResponse;
import com.webtro.modules.listing.entity.Listing;
import com.webtro.modules.listing.entity.RentalUnit;
import com.webtro.modules.listing.repository.ListingImageRepository;
import com.webtro.modules.listing.repository.ListingRepository;
import com.webtro.modules.listing.repository.RentalUnitRepository;
import com.webtro.modules.listing.service.ListingService;
import com.webtro.modules.listing.service.RentalUnitService;
import com.webtro.modules.listing.service.RentalUnitSupport;
import com.webtro.security.SecurityUtils;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Hiện thực màn "Phòng của tôi". Nghiệp vụ lõi (dò trùng, khoá địa chỉ, đồng bộ địa chỉ sang tin)
 * nằm ở {@link RentalUnitSupport}; lớp này lo phân quyền, phân trang và dựng response.
 */
@Service
@RequiredArgsConstructor
public class RentalUnitServiceImpl implements RentalUnitService {

    private final RentalUnitRepository rentalUnitRepository;
    private final ListingRepository listingRepository;
    private final ListingImageRepository imageRepository;
    private final WardRepository wardRepository;
    private final RentalUnitSupport support;
    private final ListingService listingService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RentalUnitResponse> listMine(RentalUnitStatus status, String keyword, Pageable pageable) {
        Long userId = currentUserId();
        Specification<RentalUnit> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("ownerId"), userId));
            ps.add(cb.isNull(root.get("deletedAt")));
            if (status != null) {
                ps.add(cb.equal(root.get("status"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("addressDetail")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("unitLabel"), "")), like)));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<RentalUnit> page = rentalUnitRepository.findAll(spec, pageable);
        Map<Long, List<Listing>> listingsByUnit = listingsByUnit(
                page.getContent().stream().map(RentalUnit::getId).toList());
        return PageResponse.from(page, u -> toResponse(u, listingsByUnit.getOrDefault(u.getId(), List.of()), false));
    }

    @Override
    @Transactional(readOnly = true)
    public RentalUnitResponse get(Long id) {
        RentalUnit unit = support.requireOwned(id, currentUserId(), isAdmin());
        return detail(unit);
    }

    @Override
    @Transactional
    public RentalUnitResponse create(RentalUnitRequest request) {
        Long userId = currentUserId();
        Ward ward = support.resolveWard(request.getProvinceId(), request.getDistrictId(), request.getWardId());
        RentalUnit unit = support.createUnit(userId, request.getName(), addressOf(request, ward));
        return detail(unit);
    }

    @Override
    @Transactional
    public RentalUnitResponse update(Long id, RentalUnitRequest request) {
        boolean admin = isAdmin();
        RentalUnit unit = support.requireOwned(id, currentUserId(), admin);
        Ward ward = support.resolveWard(request.getProvinceId(), request.getDistrictId(), request.getWardId());
        unit.setName(request.getName().trim());
        // unitLabel rỗng = bỏ số phòng; changeAddress coi null là "giữ nguyên" nên đổi sang "".
        RentalUnitSupport.UnitAddress address = new RentalUnitSupport.UnitAddress(ward, request.getAddressDetail(),
                request.getUnitLabel() == null ? "" : request.getUnitLabel(),
                request.getLatitude(), request.getLongitude());
        support.changeAddress(unit, address, admin, null);
        rentalUnitRepository.save(unit);
        return detail(unit);
    }

    @Override
    @Transactional
    public RentalUnitResponse archive(Long id) {
        RentalUnit unit = support.requireOwned(id, currentUserId(), isAdmin());
        if (unit.isArchived()) {
            return detail(unit);
        }
        support.findLiveListing(unit.getId()).ifPresent(live -> {
            throw new ConflictException(ErrorCode.RENTAL_UNIT_HAS_LIVE_LISTING,
                    "Phòng đang có tin \"" + live.getTitle() + "\". Hãy đóng hoặc xoá tin trước khi lưu trữ phòng.");
        });
        unit.setStatus(RentalUnitStatus.ARCHIVED);
        unit.setArchivedAt(Instant.now());
        rentalUnitRepository.save(unit);
        return detail(unit);
    }

    @Override
    @Transactional
    public RentalUnitResponse unarchive(Long id) {
        RentalUnit unit = support.requireOwned(id, currentUserId(), isAdmin());
        if (!unit.isArchived()) {
            return detail(unit);
        }
        support.findSameOwnerDuplicate(unit.getOwnerId(), unit.getWardId(), unit.getAddressDetail(),
                unit.getUnitLabel(), unit.getId()).ifPresent(existing -> {
            throw new ConflictException(ErrorCode.RENTAL_UNIT_DUPLICATE,
                    "Bạn đang có phòng \"" + existing.getName() + "\" cùng địa chỉ và số phòng. "
                            + "Hãy dùng phòng đó thay vì khôi phục phòng này.");
        });
        unit.setStatus(RentalUnitStatus.ACTIVE);
        unit.setArchivedAt(null);
        rentalUnitRepository.save(unit);
        return detail(unit);
    }

    @Override
    @Transactional(readOnly = true)
    public RentalUnitDuplicateCheckResponse checkDuplicate(Long wardId, String addressDetail, String unitLabel,
                                                           Long excludeUnitId) {
        Long userId = currentUserId();
        RentalUnit duplicate = support.findSameOwnerDuplicate(userId, wardId, addressDetail, unitLabel, excludeUnitId)
                .orElse(null);
        List<RentalUnit> sameAddress = support.findSameOwnerMatches(userId, wardId, addressDetail, unitLabel,
                excludeUnitId);
        Map<Long, List<Listing>> listingsByUnit = listingsByUnit(sameAddress.stream().map(RentalUnit::getId).toList());
        return RentalUnitDuplicateCheckResponse.builder()
                .duplicate(duplicate != null)
                .duplicateUnit(duplicate == null ? null
                        : toResponse(duplicate, listingsByUnit.getOrDefault(duplicate.getId(), List.of()), false))
                .sameAddressUnits(sameAddress.stream()
                        .filter(u -> duplicate == null || !u.getId().equals(duplicate.getId()))
                        .map(u -> toResponse(u, listingsByUnit.getOrDefault(u.getId(), List.of()), false))
                        .toList())
                .otherOwnerMatch(support.hasOtherOwnerDuplicate(userId, wardId, addressDetail, unitLabel))
                .build();
    }

    @Override
    public ListingCreateResponse relist(Long id) {
        return listingService.relist(id);
    }

    // ==================================================================
    //  Helper
    // ==================================================================

    private RentalUnitResponse detail(RentalUnit unit) {
        List<Listing> listings = listingRepository.findAllByRentalUnit(unit.getId()).stream()
                .filter(l -> l.getDeletedAt() == null)
                .toList();
        return toResponse(unit, listings, true);
    }

    private Map<Long, List<Listing>> listingsByUnit(Collection<Long> unitIds) {
        if (unitIds.isEmpty()) {
            return Map.of();
        }
        return listingRepository.findAliveByRentalUnits(unitIds).stream()
                .collect(Collectors.groupingBy(Listing::getRentalUnitId));
    }

    private RentalUnitResponse toResponse(RentalUnit u, List<Listing> listings, boolean withHistory) {
        Listing live = listings.stream()
                .filter(l -> RentalUnitSupport.LIVE_STATUSES.contains(l.getStatus()))
                .findFirst().orElse(null);
        Ward ward = wardRepository.findById(u.getWardId()).orElse(null);
        District district = ward == null ? null : ward.getDistrict();
        Province province = district == null ? null : district.getProvince();

        return RentalUnitResponse.builder()
                .id(u.getId())
                .name(u.getName())
                .unitLabel(u.getUnitLabel())
                .provinceId(province == null ? null : province.getId())
                .provinceName(province == null ? null : province.getName())
                .districtId(district == null ? null : district.getId())
                .districtName(district == null ? null : district.getName())
                .wardId(u.getWardId())
                .wardName(ward == null ? null : ward.getName())
                .addressDetail(u.getAddressDetail())
                .fullAddress(joinNonBlank(u.getAddressDetail(),
                        ward == null ? null : ward.getName(),
                        district == null ? null : district.getName(),
                        province == null ? null : province.getName()))
                .status(u.getStatus().name())
                .statusLabel(u.getStatus().getLabel())
                .archivedAt(u.getArchivedAt())
                .averageRating(u.getAverageRating())
                .reviewCount(u.getReviewCount())
                .addressLocked(u.isAddressLocked())
                .liveListing(live == null ? null : toItem(live))
                .listingCount(listings.size())
                .listings(withHistory ? listings.stream().map(this::toItem).toList() : null)
                .availableActions(actions(u, live, listings))
                .createdAt(u.getCreatedAt())
                .updatedAt(u.getUpdatedAt())
                .build();
    }

    private List<String> actions(RentalUnit u, Listing live, List<Listing> listings) {
        List<String> actions = new ArrayList<>();
        if (u.isArchived()) {
            actions.add("UNARCHIVE");
            return actions;
        }
        actions.add("EDIT");
        if (live == null) {
            actions.add("CREATE_LISTING");
            if (listings.stream().anyMatch(l -> l.getStatus() == ListingStatus.CLOSED)) {
                actions.add("RELIST");
            }
            actions.add("ARCHIVE");
        }
        return actions;
    }

    private RentalUnitResponse.ListingItem toItem(Listing l) {
        return RentalUnitResponse.ListingItem.builder()
                .id(l.getId())
                .title(l.getTitle())
                .slug(l.getSlug())
                .status(l.getStatus().name())
                .statusLabel(l.getStatus().getLabel())
                .thumbnailUrl(imageRepository.findPrimaryByListingIdAlive(l.getId())
                        .map(i -> i.getThumbnailUrl() != null ? i.getThumbnailUrl() : i.getUrl())
                        .orElse(null))
                .publishedAt(l.getPublishedAt())
                .createdAt(l.getCreatedAt())
                .build();
    }

    private RentalUnitSupport.UnitAddress addressOf(RentalUnitRequest request, Ward ward) {
        return new RentalUnitSupport.UnitAddress(ward, request.getAddressDetail(), request.getUnitLabel(),
                request.getLatitude(), request.getLongitude());
    }

    private Long currentUserId() {
        return SecurityUtils.getCurrentUserId().orElseThrow(() -> new ForbiddenException(ErrorCode.FORBIDDEN));
    }

    private boolean isAdmin() {
        return SecurityUtils.hasRole(RoleCode.ADMIN);
    }

    private static String joinNonBlank(String... parts) {
        return java.util.Arrays.stream(parts)
                .filter(p -> p != null && !p.isBlank())
                .collect(Collectors.joining(", "));
    }
}
