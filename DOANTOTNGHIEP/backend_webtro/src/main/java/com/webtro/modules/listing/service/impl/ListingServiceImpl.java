package com.webtro.modules.listing.service.impl;

import com.webtro.common.PageResponse;
import com.webtro.common.enums.CategoryCode;
import com.webtro.common.enums.ListingStatus;
import com.webtro.constant.ConfigKey;
import com.webtro.constant.ErrorCode;
import com.webtro.constant.RoleCode;
import com.webtro.exception.BusinessRuleException;
import com.webtro.exception.ConflictException;
import com.webtro.exception.ForbiddenException;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.catalog.entity.Amenity;
import com.webtro.modules.catalog.entity.Category;
import com.webtro.modules.catalog.entity.District;
import com.webtro.modules.catalog.entity.Province;
import com.webtro.modules.catalog.entity.Ward;
import com.webtro.modules.catalog.repository.AmenityRepository;
import com.webtro.modules.catalog.repository.CategoryRepository;
import com.webtro.modules.catalog.repository.DistrictRepository;
import com.webtro.modules.catalog.repository.ProvinceRepository;
import com.webtro.modules.catalog.repository.WardRepository;
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
import com.webtro.modules.listing.dto.response.RenewResponse;
import com.webtro.modules.listing.entity.Listing;
import com.webtro.modules.listing.entity.ListingAmenity;
import com.webtro.modules.listing.entity.ListingImage;
import com.webtro.modules.listing.mapper.ListingMapper;
import com.webtro.modules.listing.repository.ListingAmenityRepository;
import com.webtro.modules.listing.repository.ListingImageRepository;
import com.webtro.modules.listing.repository.ListingRepository;
import com.webtro.modules.listing.service.ListingCategoryCountPublisher;
import com.webtro.modules.listing.service.ListingOwnerStatsPublisher;
import com.webtro.modules.listing.service.ListingService;
import com.webtro.modules.listing.service.ListingVisibilityService;
import com.webtro.modules.listing.service.TrustScoreService;
import com.webtro.modules.listing.statemachine.ListingEvent;
import com.webtro.modules.listing.statemachine.ListingStateMachine;
import com.webtro.modules.payment.entity.PromotionPackage;
import com.webtro.modules.payment.repository.PromotionPackageRepository;
import com.webtro.modules.user.entity.LandlordProfile;
import com.webtro.modules.user.repository.LandlordProfileRepository;
import com.webtro.security.RateLimitService;
import com.webtro.security.SecurityUtils;
import com.webtro.storage.FileStorage;
import com.webtro.storage.StoredImage;
import com.webtro.util.HtmlSanitizer;
import com.webtro.util.SlugUtil;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Cài đặt nghiệp vụ tin đăng (docs/03 mục 4.4). Mọi chuyển trạng thái đi qua
 * {@link ListingStateMachine}; mọi truy vấn công khai dùng {@link ListingVisibilityService};
 * mọi ngưỡng đọc từ {@link SystemConfigService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListingServiceImpl implements ListingService {

    private static final ZoneId ZONE_VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ListingRepository listingRepository;
    private final ListingImageRepository imageRepository;
    private final ListingAmenityRepository amenityLinkRepository;

    private final ListingStateMachine stateMachine;
    private final ListingVisibilityService visibilityService;
    private final ListingCategoryCountPublisher categoryCountPublisher;
    private final ListingOwnerStatsPublisher ownerStatsPublisher;
    private final TrustScoreService trustScoreService;
    private final ListingMapper mapper;

    private final SystemConfigService config;
    private final RateLimitService rateLimitService;
    private final FileStorage fileStorage;
    private final StringRedisTemplate redisTemplate;

    private final CategoryRepository categoryRepository;
    private final ProvinceRepository provinceRepository;
    private final DistrictRepository districtRepository;
    private final WardRepository wardRepository;
    private final AmenityRepository amenityRepository;
    private final LandlordProfileRepository landlordProfileRepository;
    private final PromotionPackageRepository promotionPackageRepository;

    // ==================================================================
    //  CRUD
    // ==================================================================

    @Override
    @Transactional
    public ListingCreateResponse create(ListingCreateRequest req) {
        Long userId = requireCurrentUserId();

        Category category = requireActiveCategory(req.getCategoryId());
        validateCategoryAllowedForCurrentRole(category.getCode());
        validateLocation(req.getProvinceId(), req.getDistrictId(), req.getWardId());
        validateAmenities(req.getAmenityIds());
        validateTextLengths(req.getTitle(), req.getDescription());
        validateAvailableFrom(req.getAvailableFrom());
        validateRoommate(category.getCode(), req.getGenderRequirement(),
                req.getMaxOccupants(), req.getCurrentOccupants());

        enforcePostingQuota(userId);
        enforcePostingNotSuspended(userId);

        String title = HtmlSanitizer.stripAllHtml(req.getTitle());
        String description = HtmlSanitizer.stripAllHtml(req.getDescription());
        String addressDetail = HtmlSanitizer.stripAllHtml(req.getAddressDetail());

        Listing listing = Listing.builder()
                .ownerId(userId)
                .categoryId(req.getCategoryId())
                .title(title)
                .slug(uniqueSlug(title))
                .description(description)
                .price(req.getPrice())
                .area(req.getArea())
                .depositAmount(defaultZero(req.getDepositAmount()))
                .electricityPrice(req.getElectricityPrice())
                .waterPrice(req.getWaterPrice())
                .wardId(req.getWardId())
                .addressDetail(addressDetail)
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .roomCount(req.getRoomCount())
                .toiletCount(req.getToiletCount())
                .toiletType(req.getToiletType())
                .maxOccupants(req.getMaxOccupants())
                .currentOccupants(req.getCurrentOccupants())
                .genderRequirement(req.getGenderRequirement() == null
                        ? com.webtro.common.enums.GenderRequirement.ANY : req.getGenderRequirement())
                .petAllowed(Boolean.TRUE.equals(req.getPetAllowed()))
                .parkingAvailable(Boolean.TRUE.equals(req.getParkingAvailable()))
                .curfewType(req.getCurfewType() == null
                        ? com.webtro.common.enums.CurfewType.UNKNOWN : req.getCurfewType())
                .furnitureStatus(req.getFurnitureStatus() == null
                        ? com.webtro.common.enums.FurnitureStatus.NONE : req.getFurnitureStatus())
                .availableFrom(req.getAvailableFrom())
                .floorCount(req.getFloorCount())
                .status(ListingStatus.DRAFT)
                .build();
        listing = listingRepository.save(listing);
        ownerStatsPublisher.publishIfChanged(null, listing);

        replaceAmenities(listing, req.getAmenityIds());

        int displayDays = config.getInt(ConfigKey.LISTING_DISPLAY_DAYS);
        String statusName = listing.getStatus().name();

        if (Boolean.TRUE.equals(req.getSubmitImmediately())) {
            ListingActionResponse submitResult = submit(listing.getId());
            statusName = submitResult.getStatus();
        }

        int imageCount = imageRepository.findByListingIdAlive(listing.getId()).size();
        List<String> nextSteps = new ArrayList<>();
        if (imageCount < config.getInt(ConfigKey.LISTING_IMAGE_MIN)) {
            nextSteps.add("Tải lên tối thiểu 1 ảnh");
        }
        if (ListingStatus.DRAFT.name().equals(statusName)) {
            nextSteps.add("Gửi duyệt tin");
        }

        return ListingCreateResponse.builder()
                .id(listing.getId())
                .slug(listing.getSlug())
                .title(listing.getTitle())
                .status(statusName)
                .price(listing.getPrice())
                .area(listing.getArea())
                .imageCount(imageCount)
                .createdAt(listing.getCreatedAt())
                .expectedExpiredAt(listing.getExpiredAt())
                .displayDays(displayDays)
                .nextSteps(nextSteps)
                .build();
    }

    @Override
    @Transactional
    public ListingDetailResponse update(Long id, ListingUpdateRequest req) {
        Listing listing = loadAlive(id);
        requireOwnerOrUpdateAny(listing);
        if (listing.getStatus() == ListingStatus.LOCKED) {
            throw new BusinessRuleException(ErrorCode.LISTING_LOCKED_CANNOT_EDIT);
        }

        Category category = requireActiveCategory(req.getCategoryId());
        validateCategoryAllowedForCurrentRole(category.getCode());
        validateLocation(req.getProvinceId(), req.getDistrictId(), req.getWardId());
        validateAmenities(req.getAmenityIds());
        validateTextLengths(req.getTitle(), req.getDescription());
        validateAvailableFrom(req.getAvailableFrom());
        validateRoommate(category.getCode(), req.getGenderRequirement(),
                req.getMaxOccupants(), req.getCurrentOccupants());

        String newTitle = HtmlSanitizer.stripAllHtml(req.getTitle());
        String newDescription = HtmlSanitizer.stripAllHtml(req.getDescription());
        String newAddress = HtmlSanitizer.stripAllHtml(req.getAddressDetail());

        ListingStatus statusBefore = listing.getStatus();
        ListingCategoryCountPublisher.Snapshot categoryCountBefore = categoryCountPublisher.snapshot(listing);
        ListingOwnerStatsPublisher.Snapshot ownerStatsBefore = ownerStatsPublisher.snapshot(listing);
        List<Change> changes = new ArrayList<>();

        // ----- Trường nhạy cảm (đổi trên tin ACTIVE → duyệt lại) -----
        detect(changes, "title", listing.getTitle(), newTitle, true);
        detect(changes, "description", listing.getDescription(), newDescription, true);
        detect(changes, "price", str(listing.getPrice()), str(req.getPrice()), true);
        LocationParts currentLocation = locationOf(listing.getWardId());
        detect(changes, "provinceId", str(currentLocation.provinceId()), str(req.getProvinceId()), true);
        detect(changes, "districtId", str(currentLocation.districtId()), str(req.getDistrictId()), true);
        detect(changes, "wardId", str(listing.getWardId()), str(req.getWardId()), true);
        detect(changes, "addressDetail", listing.getAddressDetail(), newAddress, true);
        // ----- Trường nhỏ (giữ nguyên trạng thái) -----
        detect(changes, "categoryId", str(listing.getCategoryId()), str(req.getCategoryId()), false);
        detect(changes, "area", str(listing.getArea()), str(req.getArea()), false);
        detect(changes, "maxOccupants", str(listing.getMaxOccupants()), str(req.getMaxOccupants()), false);

        // Áp dụng thay đổi
        listing.setCategoryId(req.getCategoryId());
        listing.setTitle(newTitle);
        listing.setSlug(uniqueSlugKeepingSelf(newTitle, listing));
        listing.setDescription(newDescription);
        listing.setPrice(req.getPrice());
        listing.setArea(req.getArea());
        listing.setDepositAmount(defaultZero(req.getDepositAmount()));
        listing.setElectricityPrice(req.getElectricityPrice());
        listing.setWaterPrice(req.getWaterPrice());
        listing.setWardId(req.getWardId());
        listing.setAddressDetail(newAddress);
        listing.setLatitude(req.getLatitude());
        listing.setLongitude(req.getLongitude());
        listing.setRoomCount(req.getRoomCount());
        listing.setToiletCount(req.getToiletCount());
        listing.setToiletType(req.getToiletType());
        listing.setMaxOccupants(req.getMaxOccupants());
        listing.setCurrentOccupants(req.getCurrentOccupants());
        if (req.getGenderRequirement() != null) {
            listing.setGenderRequirement(req.getGenderRequirement());
        }
        listing.setPetAllowed(Boolean.TRUE.equals(req.getPetAllowed()));
        listing.setParkingAvailable(Boolean.TRUE.equals(req.getParkingAvailable()));
        if (req.getCurfewType() != null) {
            listing.setCurfewType(req.getCurfewType());
        }
        if (req.getFurnitureStatus() != null) {
            listing.setFurnitureStatus(req.getFurnitureStatus());
        }
        listing.setAvailableFrom(req.getAvailableFrom());
        listing.setFloorCount(req.getFloorCount());

        if (req.getAmenityIds() != null) {
            replaceAmenities(listing, req.getAmenityIds());
        }

        boolean sensitiveChanged = changes.stream().anyMatch(Change::sensitive);
        boolean requiresReapproval = sensitiveChanged && statusBefore == ListingStatus.ACTIVE;

        if (requiresReapproval) {
            ListingStatus target = stateMachine.transition(statusBefore, ListingEvent.RESUBMIT_AFTER_EDIT);
            listing.setStatus(target);
        }
        ListingStatus statusAfter = listing.getStatus();
        listingRepository.save(listing);
        categoryCountPublisher.publishIfChanged(categoryCountBefore, listing);
        ownerStatsPublisher.publishIfChanged(ownerStatsBefore, listing);

        ListingDetailResponse detail = mapper.toDetail(listing, true, true);
        List<String> sensitiveFields = changes.stream().filter(Change::sensitive)
                .map(Change::field).toList();
        detail.setModerationImpact(ModerationImpactResponse.builder()
                .requiresReapproval(requiresReapproval)
                .previousStatus(statusBefore.name())
                .newStatus(statusAfter.name())
                .sensitiveFieldsChanged(sensitiveFields)
                .reason(requiresReapproval
                        ? "Thay đổi tiêu đề, mô tả, giá, địa chỉ hoặc ảnh chính cần kiểm duyệt lại"
                        : null)
                .build());
        return detail;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Listing listing = loadAlive(id);
        requireOwnerOrUpdateAny(listing);
        if (listing.getStatus() == ListingStatus.LOCKED) {
            throw new BusinessRuleException(ErrorCode.LISTING_LOCKED_CANNOT_DELETE);
        }
        ListingCategoryCountPublisher.Snapshot categoryCountBefore = categoryCountPublisher.snapshot(listing);
        ListingOwnerStatsPublisher.Snapshot ownerStatsBefore = ownerStatsPublisher.snapshot(listing);
        ListingStatus target = stateMachine.transition(listing.getStatus(), ListingEvent.SOFT_DELETE);
        listing.setStatus(target);
        listing.softDelete();
        listingRepository.save(listing);
        categoryCountPublisher.publishIfChanged(categoryCountBefore, listing);
        ownerStatsPublisher.publishIfChanged(ownerStatsBefore, listing);
    }

    // ==================================================================
    //  Chuyển trạng thái
    // ==================================================================

    @Override
    @Transactional
    public ListingActionResponse submit(Long id) {
        Listing listing = loadAlive(id);
        Long userId = requireOwner(listing);
        if (listing.getStatus() == ListingStatus.LOCKED) {
            throw new BusinessRuleException(ErrorCode.LISTING_LOCKED_CANNOT_SUBMIT);
        }
        rateLimitService.checkLimit("listing-submit", String.valueOf(userId), 20, Duration.ofDays(1));

        int imageCount = imageRepository.findByListingIdAlive(id).size();
        if (imageCount < config.getInt(ConfigKey.LISTING_IMAGE_MIN)) {
            throw new BusinessRuleException(ErrorCode.IMAGE_COUNT_MIN);
        }
        enforcePostingNotSuspended(userId);

        // Mọi tin đều vào hàng chờ kiểm duyệt. Trước đây chủ trọ "đã xác minh + điểm uy tín cao"
        // được tự động duyệt — hai chủ trọ cùng vai trò lại đi hai luồng khác nhau, nên đã bỏ.
        ListingStatus before = listing.getStatus();
        ListingStatus pending = stateMachine.transition(before, ListingEvent.SUBMIT);
        listing.setStatus(pending);
        listingRepository.save(listing);

        return ListingActionResponse.builder()
                .id(listing.getId())
                .status(listing.getStatus().name())
                .previousStatus(before.name())
                .at(Instant.now())
                .expiredAt(listing.getExpiredAt())
                .build();
    }

    @Override
    @Transactional
    public ListingActionResponse hide(Long id) {
        Listing listing = loadAlive(id);
        requireOwner(listing);
        if (listing.getStatus() == ListingStatus.HIDDEN) {
            throw new ConflictException(ErrorCode.LISTING_ALREADY_HIDDEN);
        }
        ListingStatus before = listing.getStatus();
        ListingCategoryCountPublisher.Snapshot categoryCountBefore = categoryCountPublisher.snapshot(listing);
        ListingOwnerStatsPublisher.Snapshot ownerStatsBefore = ownerStatsPublisher.snapshot(listing);
        ListingStatus target = stateMachine.transition(before, ListingEvent.HIDE_BY_OWNER);
        listing.setStatus(target);
        listingRepository.save(listing);
        categoryCountPublisher.publishIfChanged(categoryCountBefore, listing);
        ownerStatsPublisher.publishIfChanged(ownerStatsBefore, listing);

        boolean canUnhide = listing.getExpiredAt() != null && listing.getExpiredAt().isAfter(Instant.now());
        return ListingActionResponse.builder()
                .id(listing.getId())
                .status(target.name())
                .previousStatus(before.name())
                .at(Instant.now())
                .expiredAt(listing.getExpiredAt())
                .canUnhide(canUnhide)
                .build();
    }

    @Override
    @Transactional
    public ListingActionResponse unhide(Long id) {
        Listing listing = loadAlive(id);
        requireOwner(listing);
        if (listing.getExpiredAt() != null && !listing.getExpiredAt().isAfter(Instant.now())) {
            throw new BusinessRuleException(ErrorCode.LISTING_ALREADY_EXPIRED);
        }
        ListingStatus before = listing.getStatus();
        ListingCategoryCountPublisher.Snapshot categoryCountBefore = categoryCountPublisher.snapshot(listing);
        ListingOwnerStatsPublisher.Snapshot ownerStatsBefore = ownerStatsPublisher.snapshot(listing);
        ListingStatus target = stateMachine.transition(before, ListingEvent.UNHIDE_BY_OWNER);
        listing.setStatus(target);
        listingRepository.save(listing);
        categoryCountPublisher.publishIfChanged(categoryCountBefore, listing);
        ownerStatsPublisher.publishIfChanged(ownerStatsBefore, listing);

        return ListingActionResponse.builder()
                .id(listing.getId())
                .status(target.name())
                .previousStatus(before.name())
                .at(Instant.now())
                .expiredAt(listing.getExpiredAt())
                .daysRemaining(remainingDays(listing.getExpiredAt()))
                .build();
    }

    @Override
    @Transactional
    public ListingActionResponse close(Long id, CloseListingRequest req) {
        Listing listing = loadAlive(id);
        requireOwner(listing);
        if (listing.getStatus() == ListingStatus.CLOSED) {
            throw new ConflictException(ErrorCode.LISTING_ALREADY_CLOSED);
        }
        ListingStatus before = listing.getStatus();
        ListingCategoryCountPublisher.Snapshot categoryCountBefore = categoryCountPublisher.snapshot(listing);
        ListingOwnerStatsPublisher.Snapshot ownerStatsBefore = ownerStatsPublisher.snapshot(listing);
        ListingStatus target = stateMachine.transition(before, ListingEvent.CLOSE);
        listing.setStatus(target);
        listingRepository.save(listing);
        categoryCountPublisher.publishIfChanged(categoryCountBefore, listing);
        ownerStatsPublisher.publishIfChanged(ownerStatsBefore, listing);

        return ListingActionResponse.builder()
                .id(listing.getId())
                .status(target.name())
                .previousStatus(before.name())
                .at(Instant.now())
                .closeReason(req.getReason().name())
                .build();
    }

    @Override
    @Transactional
    public RenewResponse renew(Long id, RenewListingRequest req) {
        Listing listing = loadAlive(id);
        Long userId = requireOwner(listing);
        if (listing.getStatus() == ListingStatus.LOCKED) {
            throw new BusinessRuleException(ErrorCode.LISTING_LOCKED_CANNOT_RENEW);
        }
        if (listing.getStatus() == ListingStatus.REJECTED) {
            throw new BusinessRuleException(ErrorCode.LISTING_REJECTED_MUST_EDIT);
        }
        rateLimitService.checkLimit("listing-renew", String.valueOf(userId), 10, Duration.ofDays(1));

        int displayDays = config.getInt(ConfigKey.LISTING_DISPLAY_DAYS);
        int freeLimit = config.getInt(ConfigKey.LISTING_RENEW_FREE_PER_MONTH);
        LandlordProfile profile = landlordProfileRepository.findByUser_IdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LANDLORD_PROFILE_NOT_FOUND));
        resetFreeRenewIfNewMonth(profile);
        int freeUsed = nz(profile.getFreeRenewUsedThisMonth());

        // ----- Chọn gói trả phí → tạo giao dịch (kích hoạt ở callback), không gia hạn ngay -----
        if (req.getPackageId() != null) {
            PromotionPackage pkg = promotionPackageRepository.findByIdAndDeletedAtIsNull(req.getPackageId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PACKAGE_NOT_FOUND));
            if (!Boolean.TRUE.equals(pkg.getIsActive())) {
                throw new BusinessRuleException(ErrorCode.PACKAGE_INACTIVE);
            }
            return paymentRequiredResponse(listing, freeUsed, freeLimit, List.of(pkg));
        }

        // ----- Hết lượt miễn phí → hướng sang thanh toán (200, không phải lỗi) -----
        if (freeUsed >= freeLimit) {
            return paymentRequiredResponse(listing, freeUsed, freeLimit,
                    promotionPackageRepository.findByIsActiveTrueAndDeletedAtIsNullOrderByDisplayOrderAsc());
        }

        // ----- Gia hạn miễn phí -----
        ListingStatus before = listing.getStatus();
        ListingCategoryCountPublisher.Snapshot categoryCountBefore = categoryCountPublisher.snapshot(listing);
        ListingOwnerStatsPublisher.Snapshot ownerStatsBefore = ownerStatsPublisher.snapshot(listing);
        Instant previousExpiredAt = listing.getExpiredAt();
        ListingStatus target = stateMachine.transition(before, ListingEvent.RENEW);
        listing.setStatus(target);

        Instant base = (before == ListingStatus.ACTIVE && previousExpiredAt != null
                && previousExpiredAt.isAfter(Instant.now())) ? previousExpiredAt : Instant.now();
        listing.setExpiredAt(base.plus(Duration.ofDays(displayDays)));
        listing.setRenewCount(nz(listing.getRenewCount()) + 1);
        listingRepository.save(listing);
        categoryCountPublisher.publishIfChanged(categoryCountBefore, listing);
        ownerStatsPublisher.publishIfChanged(ownerStatsBefore, listing);

        profile.setFreeRenewUsedThisMonth(freeUsed + 1);
        landlordProfileRepository.save(profile);

        int used = freeUsed + 1;
        return RenewResponse.builder()
                .id(listing.getId())
                .status(target.name())
                .previousStatus(before.name())
                .previousExpiredAt(previousExpiredAt)
                .expiredAt(listing.getExpiredAt())
                .displayDays(displayDays)
                .free(true)
                .freeRenewUsed(used)
                .freeRenewLimit(freeLimit)
                .freeRenewRemaining(Math.max(0, freeLimit - used))
                .paymentRequired(false)
                .build();
    }

    // ==================================================================
    //  Đọc
    // ==================================================================

    @Override
    @Transactional
    public ListingDetailResponse getDetail(Long id, boolean countView, String clientIp) {
        boolean privileged = SecurityUtils.hasAnyRole(RoleCode.MODERATOR, RoleCode.ADMIN);
        Listing listing = listingRepository.findAliveById(id)
                .orElseGet(() -> privileged ? listingRepository.findAnyById(id).orElse(null) : null);
        if (listing == null) {
            throw new ResourceNotFoundException(ErrorCode.LISTING_NOT_FOUND);
        }
        Long viewerId = SecurityUtils.getCurrentUserId().orElse(null);
        boolean owner = viewerId != null && viewerId.equals(listing.getOwnerId());
        if (!visibilityService.publiclyVisible(listing) && !owner && !privileged) {
            // Không tiết lộ tin tồn tại (canonical §11.1).
            throw new ResourceNotFoundException(ErrorCode.LISTING_NOT_FOUND);
        }

        if (countView && visibilityService.publiclyVisible(listing)) {
            maybeCountView(listing, viewerId, clientIp);
        }

        boolean authenticated = viewerId != null;
        return mapper.toDetail(listing, authenticated, owner || privileged);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ListingSummaryResponse> getMyListings(List<ListingStatus> statuses, String keyword,
                                                              Long categoryId, Integer expiringWithinDays,
                                                              Pageable pageable) {
        Long userId = requireCurrentUserId();
        Specification<Listing> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("ownerId"), userId));
            if (statuses != null && !statuses.isEmpty()) {
                ps.add(root.get("status").in(statuses));
            } else {
                // Mặc định: tất cả trừ DELETED.
                ps.add(cb.notEqual(root.get("status"), ListingStatus.DELETED));
                ps.add(cb.isNull(root.get("deletedAt")));
            }
            if (keyword != null && !keyword.isBlank()) {
                ps.add(cb.like(cb.lower(root.get("title")), "%" + keyword.toLowerCase() + "%"));
            }
            if (categoryId != null) {
                ps.add(cb.equal(root.get("categoryId"), categoryId));
            }
            if (expiringWithinDays != null) {
                Instant threshold = Instant.now().plus(Duration.ofDays(expiringWithinDays));
                ps.add(cb.equal(root.get("status"), ListingStatus.ACTIVE));
                ps.add(cb.isNotNull(root.get("expiredAt")));
                ps.add(cb.lessThanOrEqualTo(root.get("expiredAt"), threshold));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<Listing> page = listingRepository.findAll(spec, pageable);
        return PageResponse.from(page, l -> mapper.toSummary(l, true));
    }

    @Override
    @Transactional(readOnly = true)
    public ListingStatsResponse getStats(Long id, LocalDate from, LocalDate to) {
        Listing listing = loadAlive(id);
        boolean owner = SecurityUtils.getCurrentUserId().map(uid -> uid.equals(listing.getOwnerId()))
                .orElse(false);
        if (!owner && !SecurityUtils.hasRole(RoleCode.ADMIN)) {
            throw new ForbiddenException(ErrorCode.LISTING_FORBIDDEN);
        }
        if (from != null && to != null && to.isBefore(from)) {
            throw new BusinessRuleException(ErrorCode.STATISTIC_RANGE_INVALID);
        }

        int views = nz(listing.getViewCount());
        int contacts = nz(listing.getContactCount());
        BigDecimal conversion = views == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(contacts).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(views), 2, RoundingMode.HALF_UP);

        ListingStatsResponse.Summary summary = ListingStatsResponse.Summary.builder()
                .viewCount(views)
                .favoriteCount(nz(listing.getFavoriteCount()))
                .contactCount(contacts)
                .commentCount(nz(listing.getCommentCount()))
                .reviewCount(nz(listing.getReviewCount()))
                .averageRating(listing.getAverageRating())
                .trustScore(intOf(listing.getTrustScore()))
                .conversionRatePercent(conversion)
                .daysActive(daysBetween(listing.getPublishedAt(), Instant.now()))
                .daysRemaining(remainingDays(listing.getExpiredAt()))
                .build();

        int comment = nz(listing.getCommentCount());
        int positive = nz(listing.getPositiveCommentCount());
        int negative = nz(listing.getNegativeCommentCount());
        int neutral = Math.max(0, comment - positive - negative);
        BigDecimal negativeRatio = comment == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(negative).divide(BigDecimal.valueOf(comment), 3, RoundingMode.HALF_UP);
        BigDecimal ratioL1 = config.getDecimal(ConfigKey.AI_SENTIMENT_NEGATIVE_RATIO_L1);
        int minCommentsL1 = config.getInt(ConfigKey.AI_SENTIMENT_MIN_COMMENTS_L1);
        boolean flagged = comment >= minCommentsL1 && negativeRatio.compareTo(ratioL1) >= 0;

        ListingStatsResponse.SentimentSummary sentiment = ListingStatsResponse.SentimentSummary.builder()
                .positive(positive)
                .neutral(neutral)
                .negative(negative)
                .negativeRatio(negativeRatio)
                .needReviewThresholdRatio(ratioL1)
                .flagged(flagged)
                .build();

        ListingStatsResponse.Promotion promotion = ListingStatsResponse.Promotion.builder()
                .promoted(Boolean.TRUE.equals(listing.getIsPromoted()))
                .priority(nz(listing.getPromotionPriority()))
                .endAt(listing.getPromotedUntil())
                .daysRemaining(remainingDays(listing.getPromotedUntil()))
                .build();

        return ListingStatsResponse.builder()
                .listingId(listing.getId())
                .title(listing.getTitle())
                .status(listing.getStatus().name())
                .summary(summary)
                .sentimentSummary(sentiment)
                .promotion(promotion)
                .timeSeries(List.of())
                .build();
    }

    // ==================================================================
    //  Ảnh & tiện ích
    // ==================================================================

    @Override
    @Transactional
    public ImageUploadResponse uploadImages(Long id, List<MultipartFile> files, Integer setPrimaryIndex) {
        Listing listing = loadAlive(id);
        Long userId = requireOwnerOrUpdateAny(listing);
        if (listing.getStatus() == ListingStatus.LOCKED) {
            throw new BusinessRuleException(ErrorCode.LISTING_LOCKED_CANNOT_EDIT);
        }
        if (files == null || files.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.IMAGE_COUNT_MIN);
        }
        rateLimitService.checkLimit("listing-image-upload", String.valueOf(userId), 50, Duration.ofHours(1));

        int maxImages = config.getInt(ConfigKey.LISTING_IMAGE_MAX);
        long maxBytes = (long) config.getInt(ConfigKey.LISTING_IMAGE_MAX_SIZE_MB) * 1024 * 1024;
        List<ListingImage> existing = imageRepository.findByListingIdAlive(id);
        if (files.size() > maxImages || existing.size() + files.size() > maxImages) {
            throw new BusinessRuleException(ErrorCode.IMAGE_COUNT_MAX);
        }
        for (MultipartFile f : files) {
            if (f.getSize() > maxBytes) {
                throw new BusinessRuleException(ErrorCode.IMAGE_TOO_LARGE);
            }
            if (!isAllowedImage(f)) {
                throw new BusinessRuleException(ErrorCode.IMAGE_INVALID_FORMAT);
            }
        }

        boolean hasPrimary = existing.stream().anyMatch(i -> Boolean.TRUE.equals(i.getIsPrimary()));
        int order = existing.stream().mapToInt(i -> nz(i.getDisplayOrder())).max().orElse(0);

        List<ImageUploadResponse.Item> items = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            MultipartFile f = files.get(i);
            StoredImage stored = fileStorage.storeImage(f, "listings/" + id);
            order++;
            boolean primary = !hasPrimary && (setPrimaryIndex == null ? i == 0 : setPrimaryIndex == i);
            if (primary) {
                hasPrimary = true;
            }
            ListingImage image = ListingImage.builder()
                    .listing(listing)
                    .url(stored.getOriginalUrl())
                    .thumbnailUrl(stored.getThumbnailUrl())
                    .isPrimary(primary)
                    .displayOrder(order)
                    .originalName(f.getOriginalFilename())
                    .fileSize((int) Math.min(Integer.MAX_VALUE, stored.getSizeBytes()))
                    .contentType(f.getContentType() == null ? "image/webp" : f.getContentType())
                    .width(stored.getWidth())
                    .height(stored.getHeight())
                    .build();
            image = imageRepository.save(image);
            items.add(ImageUploadResponse.Item.builder()
                    .id(image.getId())
                    .url(image.getUrl())
                    .thumbnailUrl(image.getThumbnailUrl())
                    .primary(image.getIsPrimary())
                    .displayOrder(image.getDisplayOrder())
                    .sizeBytes(stored.getSizeBytes())
                    .width(image.getWidth())
                    .height(image.getHeight())
                    .build());
        }

        int total = imageRepository.findByListingIdAlive(id).size();
        return ImageUploadResponse.builder()
                .listingId(id)
                .totalImages(total)
                .maxImages(maxImages)
                .uploaded(items)
                .build();
    }

    @Override
    @Transactional
    public void deleteImage(Long id, Long imageId, Long newPrimaryImageId) {
        Listing listing = loadAlive(id);
        requireOwnerOrUpdateAny(listing);
        if (listing.getStatus() == ListingStatus.LOCKED) {
            throw new BusinessRuleException(ErrorCode.LISTING_LOCKED_CANNOT_EDIT);
        }
        List<ListingImage> images = imageRepository.findByListingIdAlive(id);
        ListingImage target = images.stream().filter(i -> i.getId().equals(imageId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.IMAGE_NOT_FOUND));

        boolean publicListing = visibilityService.publiclyVisible(listing);
        if (publicListing && images.size() <= config.getInt(ConfigKey.LISTING_IMAGE_MIN)) {
            throw new BusinessRuleException(ErrorCode.IMAGE_COUNT_MIN);
        }
        if (Boolean.TRUE.equals(target.getIsPrimary()) && images.size() > 1) {
            if (newPrimaryImageId == null) {
                throw new BusinessRuleException(ErrorCode.PRIMARY_IMAGE_REQUIRED);
            }
            ListingImage newPrimary = images.stream()
                    .filter(i -> i.getId().equals(newPrimaryImageId) && !i.getId().equals(imageId))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.IMAGE_NOT_FOUND));
            images.forEach(i -> i.setIsPrimary(false));
            newPrimary.setIsPrimary(true);
            imageRepository.saveAll(images);
        }
        target.softDelete();
        imageRepository.save(target);
    }

    @Override
    @Transactional
    public ModerationImpactResponse setPrimaryImage(Long id, Long imageId) {
        Listing listing = loadAlive(id);
        requireOwnerOrUpdateAny(listing);
        if (listing.getStatus() == ListingStatus.LOCKED) {
            throw new BusinessRuleException(ErrorCode.LISTING_LOCKED_CANNOT_EDIT);
        }
        List<ListingImage> images = imageRepository.findByListingIdAlive(id);
        ListingImage target = images.stream().filter(i -> i.getId().equals(imageId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.IMAGE_NOT_FOUND));
        images.forEach(i -> i.setIsPrimary(false));
        target.setIsPrimary(true);
        imageRepository.saveAll(images);

        ListingStatus before = listing.getStatus();
        ListingCategoryCountPublisher.Snapshot categoryCountBefore = categoryCountPublisher.snapshot(listing);
        ListingOwnerStatsPublisher.Snapshot ownerStatsBefore = ownerStatsPublisher.snapshot(listing);
        boolean requiresReapproval = before == ListingStatus.ACTIVE;
        if (requiresReapproval) {
            listing.setStatus(stateMachine.transition(before, ListingEvent.RESUBMIT_AFTER_EDIT));
            listingRepository.save(listing);
            categoryCountPublisher.publishIfChanged(categoryCountBefore, listing);
            ownerStatsPublisher.publishIfChanged(ownerStatsBefore, listing);
        }
        return ModerationImpactResponse.builder()
                .requiresReapproval(requiresReapproval)
                .previousStatus(before.name())
                .newStatus(listing.getStatus().name())
                .sensitiveFieldsChanged(List.of("primaryImage"))
                .reason(requiresReapproval ? "Đổi ảnh chính cần kiểm duyệt lại" : null)
                .build();
    }

    @Override
    @Transactional
    public List<ListingImageResponseOrder> reorderImages(Long id, ImageOrderRequest req) {
        Listing listing = loadAlive(id);
        requireOwnerOrUpdateAny(listing);
        if (listing.getStatus() == ListingStatus.LOCKED) {
            throw new BusinessRuleException(ErrorCode.LISTING_LOCKED_CANNOT_EDIT);
        }
        List<ListingImage> images = imageRepository.findByListingIdAlive(id);
        Set<Long> aliveIds = new HashSet<>();
        images.forEach(i -> aliveIds.add(i.getId()));
        Set<Long> requested = new HashSet<>(req.getImageIds());
        if (requested.size() != req.getImageIds().size() || !requested.equals(aliveIds)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Danh sách ảnh phải chứa đúng đủ tập ảnh hiện có của tin");
        }
        int order = 0;
        List<ListingImageResponseOrder> result = new ArrayList<>();
        for (Long imageId : req.getImageIds()) {
            order++;
            ListingImage image = images.stream().filter(i -> i.getId().equals(imageId)).findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.IMAGE_NOT_FOUND));
            image.setDisplayOrder(order);
            imageRepository.save(image);
            result.add(new ListingImageResponseOrder(image.getId(), image.getDisplayOrder(),
                    image.getIsPrimary()));
        }
        return result;
    }

    @Override
    @Transactional
    public List<ListingAmenityResponse> updateAmenities(Long id, AmenityUpdateRequest req) {
        Listing listing = loadAlive(id);
        requireOwnerOrUpdateAny(listing);
        if (listing.getStatus() == ListingStatus.LOCKED) {
            throw new BusinessRuleException(ErrorCode.LISTING_LOCKED_CANNOT_EDIT);
        }
        validateAmenities(req.getAmenityIds());
        replaceAmenities(listing, req.getAmenityIds());
        return mapper.amenitiesOf(id);
    }

    // ==================================================================
    //  Dùng bởi module khác
    // ==================================================================

    @Override
    @Transactional(readOnly = true)
    public Listing getActiveById(Long id) {
        Listing listing = listingRepository.findAliveById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LISTING_NOT_FOUND));
        if (!visibilityService.publiclyVisible(listing)) {
            throw new BusinessRuleException(ErrorCode.LISTING_NOT_ACTIVE);
        }
        return listing;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<ListingStatus> getPublicStatuses() {
        return visibilityService.publicStatuses();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean publiclyVisible(Long listingId) {
        return listingRepository.findAliveById(listingId)
                .map(visibilityService::publiclyVisible)
                .orElse(false);
    }

    @Override
    @Transactional
    public void incrementFavoriteCount(Long listingId, int delta) {
        Listing listing = loadAlive(listingId);
        listing.setFavoriteCount(Math.max(0, nz(listing.getFavoriteCount()) + delta));
        listingRepository.save(listing);
    }

    @Override
    @Transactional
    public void incrementContactCount(Long listingId) {
        Listing listing = loadAlive(listingId);
        listing.setContactCount(nz(listing.getContactCount()) + 1);
        listingRepository.save(listing);
    }

    // ==================================================================
    //  Helper riêng tư
    // ==================================================================

    private Listing loadAlive(Long id) {
        return listingRepository.findAliveById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LISTING_NOT_FOUND));
    }

    private Long requireCurrentUserId() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new ForbiddenException(ErrorCode.FORBIDDEN));
    }

    private Long requireOwner(Listing listing) {
        Long userId = requireCurrentUserId();
        if (!userId.equals(listing.getOwnerId())) {
            throw new ForbiddenException(ErrorCode.LISTING_FORBIDDEN);
        }
        return userId;
    }

    private Long requireOwnerOrUpdateAny(Listing listing) {
        Long userId = requireCurrentUserId();
        if (!userId.equals(listing.getOwnerId())
                && !SecurityUtils.hasRole(RoleCode.ADMIN)) {
            throw new ForbiddenException(ErrorCode.LISTING_FORBIDDEN);
        }
        return userId;
    }

    private void validateCategoryAllowedForCurrentRole(CategoryCode categoryCode) {
        if (SecurityUtils.hasRole(RoleCode.TENANT) && categoryCode != CategoryCode.ROOMMATE) {
            throw new ForbiddenException(ErrorCode.LISTING_FORBIDDEN,
                    "Người thuê chỉ được đăng tin loại tìm người ở ghép");
        }
    }

    private Category requireActiveCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CATEGORY_NOT_FOUND));
        if (!Boolean.TRUE.equals(category.getIsActive())) {
            throw new ResourceNotFoundException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        return category;
    }

    private void validateLocation(Long provinceId, Long districtId, Long wardId) {
        Province province = provinceRepository.findById(provinceId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PROVINCE_NOT_FOUND));
        if (!Boolean.TRUE.equals(province.getIsActive())) {
            throw new BusinessRuleException(ErrorCode.AREA_NOT_SUPPORTED);
        }
        District district = districtRepository.findById(districtId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.DISTRICT_NOT_FOUND));
        if (district.getProvince() == null || !Objects.equals(district.getProvince().getId(), provinceId)) {
            throw new BusinessRuleException(ErrorCode.ADDRESS_HIERARCHY_MISMATCH);
        }
        Ward ward = wardRepository.findById(wardId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WARD_NOT_FOUND));
        if (ward.getDistrict() == null || !Objects.equals(ward.getDistrict().getId(), districtId)) {
            throw new BusinessRuleException(ErrorCode.ADDRESS_HIERARCHY_MISMATCH);
        }
    }

    private LocationParts locationOf(Long wardId) {
        Ward ward = wardId == null ? null : wardRepository.findById(wardId).orElse(null);
        District district = ward == null ? null : ward.getDistrict();
        Province province = district == null ? null : district.getProvince();
        return new LocationParts(
                province == null ? null : province.getId(),
                district == null ? null : district.getId());
    }

    private record LocationParts(Long provinceId, Long districtId) {
    }

    private void validateAmenities(List<Long> amenityIds) {
        if (amenityIds == null || amenityIds.isEmpty()) {
            return;
        }
        List<Amenity> found = amenityRepository.findAllById(new HashSet<>(amenityIds));
        Set<Long> activeIds = new HashSet<>();
        found.stream().filter(a -> Boolean.TRUE.equals(a.getIsActive()))
                .forEach(a -> activeIds.add(a.getId()));
        for (Long amenityId : amenityIds) {
            if (!activeIds.contains(amenityId)) {
                throw new ResourceNotFoundException(ErrorCode.AMENITY_NOT_FOUND,
                        "Không tìm thấy hoặc tiện ích không còn hoạt động: id = " + amenityId);
            }
        }
    }

    private void validateTextLengths(String title, String description) {
        String t = title == null ? "" : title.trim();
        String d = description == null ? "" : description.trim();
        int titleMin = config.getInt(ConfigKey.LISTING_TITLE_MIN);
        int titleMax = config.getInt(ConfigKey.LISTING_TITLE_MAX);
        int descMin = config.getInt(ConfigKey.LISTING_DESCRIPTION_MIN);
        int descMax = config.getInt(ConfigKey.LISTING_DESCRIPTION_MAX);
        if (t.length() < titleMin || t.length() > titleMax) {
            throw new BusinessRuleException(ErrorCode.INVALID_TITLE_LENGTH);
        }
        if (d.length() < descMin || d.length() > descMax) {
            throw new BusinessRuleException(ErrorCode.INVALID_DESCRIPTION_LENGTH);
        }
    }

    private void validateAvailableFrom(LocalDate availableFrom) {
        if (availableFrom != null && availableFrom.isBefore(LocalDate.now(ZONE_VN))) {
            throw new BusinessRuleException(ErrorCode.INVALID_AVAILABLE_FROM);
        }
    }

    private void validateRoommate(CategoryCode code, com.webtro.common.enums.GenderRequirement gender,
                                  Integer maxOccupants, Integer currentOccupants) {
        if (code == CategoryCode.ROOMMATE
                && (gender == null || maxOccupants == null || currentOccupants == null)) {
            throw new BusinessRuleException(ErrorCode.ROOMMATE_INFO_REQUIRED);
        }
    }

    /**
     * Hạn mức đăng tin trong ngày — MỘT ngưỡng duy nhất cho mọi người dùng.
     *
     * <p>Trước đây tài khoản dưới 7 ngày tuổi chịu hạn mức thấp hơn; điều đó khiến hai chủ trọ
     * cùng vai trò lại có mức sử dụng khác nhau nên đã bỏ.
     */
    private void enforcePostingQuota(Long userId) {
        Instant startOfDay = LocalDate.now(ZONE_VN).atStartOfDay(ZONE_VN).toInstant();
        Specification<Listing> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("ownerId"), userId),
                cb.greaterThanOrEqualTo(root.get("createdAt"), startOfDay));
        long todayCount = listingRepository.count(spec);

        if (todayCount >= config.getInt(ConfigKey.SPAM_LISTING_DAILY)) {
            throw new BusinessRuleException(ErrorCode.LISTING_QUOTA_DAILY);
        }
    }

    /**
     * Chặn đăng tin khi đang bị kiểm duyệt viên đình chỉ CÓ THỜI HẠN.
     *
     * <p>Đây là chế tài tạm thời do hành vi vi phạm, có ngày hết hạn rõ ràng — không phải sự khác
     * biệt về bộ chức năng giữa hai người cùng vai trò. Nhánh cũ "đủ số lần cảnh cáo thì cấm đăng
     * vĩnh viễn" đã bỏ vì nó khoá chức năng theo từng người và không có đường hồi phục.
     */
    private void enforcePostingNotSuspended(Long userId) {
        landlordProfileRepository.findByUser_IdAndDeletedAtIsNull(userId).ifPresent(profile -> {
            if (profile.getPostingRestrictedUntil() != null
                    && profile.getPostingRestrictedUntil().isAfter(Instant.now())) {
                throw new ForbiddenException(ErrorCode.LISTING_POSTING_SUSPENDED);
            }
        });
    }

    private void maybeCountView(Listing listing, Long viewerId, String clientIp) {
        String identifier = viewerId != null ? "u:" + viewerId
                : "ip:" + Integer.toHexString(String.valueOf(clientIp).hashCode());
        String key = "view:dedup:" + listing.getId() + ":" + identifier;
        int dedupMinutes = config.getInt(ConfigKey.VIEW_DEDUP_MINUTES);
        try {
            Boolean firstView = redisTemplate.opsForValue()
                    .setIfAbsent(key, "1", Duration.ofMinutes(dedupMinutes));
            if (Boolean.TRUE.equals(firstView)) {
                listing.setViewCount(nz(listing.getViewCount()) + 1);
                listingRepository.save(listing);
            }
        } catch (RuntimeException e) {
            // Fail-open: Redis lỗi thì vẫn đếm một lượt, không chặn xem chi tiết.
            log.warn("Redis lỗi khi khử trùng lượt xem listing={} ({}), vẫn đếm 1 lượt",
                    listing.getId(), e.getMessage());
            listing.setViewCount(nz(listing.getViewCount()) + 1);
            listingRepository.save(listing);
        }
    }

    /**
     * Kiểm tra ảnh hợp lệ bằng <b>magic bytes</b> (không tin {@code Content-Type} do client gửi) —
     * chống upload file thực thi đổi đuôi (canonical §8, §11.1). Chấp nhận JPG/PNG/WEBP.
     */
    private boolean isAllowedImage(MultipartFile file) {
        try {
            byte[] header = new byte[12];
            int read;
            try (var in = file.getInputStream()) {
                read = in.readNBytes(header, 0, 12);
            }
            if (read < 12) {
                return false;
            }
            // JPG: FF D8 FF
            if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
                return true;
            }
            // PNG: 89 50 4E 47 0D 0A 1A 0A
            if ((header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') {
                return true;
            }
            // WEBP: "RIFF" .... "WEBP"
            return header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
        } catch (java.io.IOException e) {
            return false;
        }
    }

    private RenewResponse paymentRequiredResponse(Listing listing, int freeUsed, int freeLimit,
                                                  List<PromotionPackage> packages) {
        List<RenewResponse.PackageOption> options = packages.stream()
                .map(p -> RenewResponse.PackageOption.builder()
                        .id(p.getId())
                        .code(p.getCode())
                        .name(p.getName())
                        .price(p.getPrice())
                        .durationDays(p.getDurationDays())
                        .build())
                .toList();
        return RenewResponse.builder()
                .id(listing.getId())
                .status(listing.getStatus().name())
                .expiredAt(listing.getExpiredAt())
                .free(false)
                .freeRenewUsed(freeUsed)
                .freeRenewLimit(freeLimit)
                .freeRenewRemaining(Math.max(0, freeLimit - freeUsed))
                .paymentRequired(true)
                .paymentHint(RenewResponse.PaymentHint.builder()
                        .endpoint("POST /api/payments")
                        .purpose("RENEW")
                        .availablePackages(options)
                        .build())
                .build();
    }

    private void resetFreeRenewIfNewMonth(LandlordProfile profile) {
        LocalDate today = LocalDate.now(ZONE_VN);
        LocalDate resetAt = profile.getFreeRenewResetAt();
        if (resetAt == null || resetAt.getMonthValue() != today.getMonthValue()
                || resetAt.getYear() != today.getYear()) {
            profile.setFreeRenewUsedThisMonth(0);
            profile.setFreeRenewResetAt(today);
        }
    }

    private void replaceAmenities(Listing listing, List<Long> amenityIds) {
        List<ListingAmenity> existing = amenityLinkRepository.findByListingIdAlive(listing.getId());
        existing.forEach(link -> {
            link.softDelete();
            amenityLinkRepository.save(link);
        });
        if (amenityIds == null) {
            return;
        }
        Set<Long> distinct = new java.util.LinkedHashSet<>(amenityIds);
        for (Long amenityId : distinct) {
            ListingAmenity link = ListingAmenity.builder()
                    .listing(listing)
                    .amenityId(amenityId)
                    .build();
            amenityLinkRepository.save(link);
        }
    }

    private String uniqueSlug(String title) {
        String base = SlugUtil.toSlug(title);
        String candidate = base;
        int suffix = 1;
        while (listingRepository.existsBySlugAndDeletedAtIsNull(candidate)) {
            suffix++;
            candidate = base + "-" + suffix;
        }
        return candidate;
    }

    private String uniqueSlugKeepingSelf(String title, Listing self) {
        String base = SlugUtil.toSlug(title);
        if (base.equals(self.getSlug()) || self.getSlug().startsWith(base + "-")) {
            return self.getSlug();
        }
        return uniqueSlug(title);
    }

    private void detect(List<Change> changes, String field, String oldValue, String newValue,
                        boolean sensitive) {
        if (!Objects.equals(oldValue, newValue)) {
            changes.add(new Change(field, oldValue, newValue, sensitive));
        }
    }

    private static BigDecimal defaultZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static int intOf(BigDecimal v) {
        return v == null ? 0 : v.setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private static Long remainingDays(Instant until) {
        if (until == null) {
            return null;
        }
        return Math.max(0, Duration.between(Instant.now(), until).toDays());
    }

    private static Long daysBetween(Instant from, Instant to) {
        if (from == null) {
            return null;
        }
        return Math.max(0, Duration.between(from, to).toDays());
    }

    /** Bản ghi một thay đổi field khi sửa tin. */
    private record Change(String field, String oldValue, String newValue, boolean sensitive) {
    }

    /** So sánh ảnh theo thứ tự hiển thị (giữ ổn định khi map danh sách ảnh). */
    @SuppressWarnings("unused")
    private static final Comparator<ListingImage> BY_ORDER =
            Comparator.comparing(ListingImage::getDisplayOrder,
                    Comparator.nullsLast(Comparator.naturalOrder()));
}
