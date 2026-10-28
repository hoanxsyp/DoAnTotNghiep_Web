package com.webtro.modules.rental.service.impl;

import com.webtro.common.PageResponse;
import com.webtro.common.enums.ListingStatus;
import com.webtro.common.enums.NotificationType;
import com.webtro.common.enums.RentalRequestStatus;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessRuleException;
import com.webtro.exception.ConflictException;
import com.webtro.exception.ForbiddenException;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.interaction.spi.ListingGateway;
import com.webtro.modules.interaction.spi.UserGateway;
import com.webtro.modules.notification.service.NotificationService;
import com.webtro.modules.rental.dto.request.CreateRentalRequest;
import com.webtro.modules.rental.dto.request.RentalDecisionRequest;
import com.webtro.modules.rental.dto.request.UpdateViewingDateRequest;
import com.webtro.modules.rental.dto.response.RentalRequestResponse;
import com.webtro.modules.rental.entity.RentalRequest;
import com.webtro.modules.rental.repository.RentalRequestRepository;
import com.webtro.modules.rental.service.RentalRequestService;
import com.webtro.util.HtmlSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RentalRequestServiceImpl implements RentalRequestService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final List<RentalRequestStatus> OPEN_STATUSES = List.of(
            RentalRequestStatus.PENDING, RentalRequestStatus.APPROVED, RentalRequestStatus.RENTED);
    private static final List<RentalRequestStatus> WAITING_STATUSES = List.of(
            RentalRequestStatus.PENDING, RentalRequestStatus.APPROVED);

    private final RentalRequestRepository rentalRequestRepository;
    private final ListingGateway listingGateway;
    private final UserGateway userGateway;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public RentalRequestResponse create(CreateRentalRequest request, Long tenantId) {
        ListingGateway.ListingBrief listing = listingGateway.getBrief(request.getListingId());
        userGateway.getBrief(tenantId);
        if (listing.ownerId().equals(tenantId)) {
            throw new BusinessRuleException(ErrorCode.CONTACT_FORBIDDEN_SELF,
                    "Bạn không thể gửi yêu cầu thuê tin đăng của chính mình");
        }
        if (listing.status() != ListingStatus.ACTIVE || !listing.publiclyVisible()) {
            throw new ConflictException(ErrorCode.RENTAL_LISTING_UNAVAILABLE);
        }
        // Tenant đăng tin ở ghép không có màn duyệt yêu cầu thuê → liên hệ qua chat/SĐT (BUG-10).
        if (!userGateway.hasLandlordProfile(listing.ownerId())) {
            throw new BusinessRuleException(ErrorCode.RENTAL_NOT_SUPPORTED_FOR_ROOMMATE);
        }
        if (request.getDesiredViewingDate().isBefore(businessToday())) {
            throw new BusinessRuleException(ErrorCode.RENTAL_VIEWING_DATE_INVALID);
        }
        if (rentalRequestRepository.existsByListingIdAndTenantIdAndStatusInAndDeletedAtIsNull(
                listing.id(), tenantId, OPEN_STATUSES)) {
            throw new ConflictException(ErrorCode.RENTAL_REQUEST_ALREADY_OPEN);
        }
        RentalRequest rental = RentalRequest.builder()
                .listingId(listing.id())
                .rentalUnitId(listing.rentalUnitId())
                .tenantId(tenantId)
                .desiredViewingDate(request.getDesiredViewingDate())
                .status(RentalRequestStatus.PENDING)
                .build();
        try {
            rental = rentalRequestRepository.saveAndFlush(rental);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException(ErrorCode.RENTAL_REQUEST_ALREADY_OPEN);
        }

        UserGateway.UserBrief tenant = userGateway.getBrief(tenantId);
        notificationService.notifyUser(listing.ownerId(), NotificationType.RENTAL_REQUEST_CREATED,
                "Yêu cầu thuê phòng mới",
                tenant.fullName() + " muốn xem phòng \"" + listing.title() + "\" vào ngày "
                        + request.getDesiredViewingDate(),
                "/quan-ly/yeu-cau-thue", null);
        return toResponse(rental, listing, tenant);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RentalRequestResponse> listMine(
            Long tenantId, RentalRequestStatus status, Pageable pageable) {
        Page<RentalRequest> page = status == null
                ? rentalRequestRepository.findByTenantIdAndDeletedAtIsNull(tenantId, pageable)
                : rentalRequestRepository.findByTenantIdAndStatusAndDeletedAtIsNull(tenantId, status, pageable);
        return mapPage(page);
    }

    @Override
    @Transactional
    public RentalRequestResponse cancel(Long requestId, Long tenantId) {
        RentalRequest rental = locked(requestId);
        if (!rental.getTenantId().equals(tenantId)) {
            throw new ForbiddenException(ErrorCode.RENTAL_REQUEST_FORBIDDEN);
        }
        requireStatus(rental, RentalRequestStatus.PENDING, RentalRequestStatus.APPROVED);
        if (rental.getStatus() == RentalRequestStatus.APPROVED
                && !rental.getDesiredViewingDate().isAfter(businessToday().plusDays(1))) {
            throw new BusinessRuleException(ErrorCode.RENTAL_CANCELLATION_DEADLINE_PASSED);
        }
        ListingGateway.ListingBrief listing = listingGateway.getBrief(rental.getListingId());
        UserGateway.UserBrief tenant = userGateway.getBrief(tenantId);
        rental.setStatus(RentalRequestStatus.CANCELLED);
        rental.setCancelledAt(Instant.now());
        rental = rentalRequestRepository.save(rental);
        notificationService.notifyUser(listing.ownerId(), NotificationType.RENTAL_REQUEST_CANCELLED,
                "Người thuê đã hủy yêu cầu",
                tenant.fullName() + " đã hủy yêu cầu xem phòng \"" + listing.title() + "\" vào ngày "
                        + rental.getDesiredViewingDate(),
                "/quan-ly/yeu-cau-thue", null);
        return toResponse(rental, listing, tenant);
    }

    @Override
    @Transactional
    public RentalRequestResponse updateViewingDate(
            Long requestId, Long tenantId, UpdateViewingDateRequest request) {
        RentalRequest rental = locked(requestId);
        if (!rental.getTenantId().equals(tenantId)) {
            throw new ForbiddenException(ErrorCode.RENTAL_REQUEST_FORBIDDEN);
        }
        requireStatus(rental, RentalRequestStatus.PENDING);
        if (request.getDesiredViewingDate().isBefore(businessToday())) {
            throw new BusinessRuleException(ErrorCode.RENTAL_VIEWING_DATE_INVALID);
        }
        rental.setDesiredViewingDate(request.getDesiredViewingDate());
        rental = rentalRequestRepository.save(rental);
        return toResponse(rental);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RentalRequestResponse> listForLandlord(
            Long ownerId, RentalRequestStatus status, Pageable pageable) {
        Page<RentalRequest> page = status == null
                ? rentalRequestRepository.findForLandlord(ownerId, pageable)
                : rentalRequestRepository.findForLandlordByStatus(ownerId, status, pageable);
        return mapPage(page);
    }

    @Override
    @Transactional
    public RentalRequestResponse approve(Long requestId, Long ownerId, RentalDecisionRequest request) {
        RentalRequest rental = lockedForOwner(requestId, ownerId);
        requireStatus(rental, RentalRequestStatus.PENDING);
        ListingGateway.ListingBrief listing = listingGateway.getBrief(rental.getListingId());
        if (listing.status() != ListingStatus.ACTIVE) {
            throw new ConflictException(ErrorCode.RENTAL_LISTING_UNAVAILABLE);
        }
        rental.setStatus(RentalRequestStatus.APPROVED);
        rental.setLandlordNote(cleanNote(request));
        rental.setApprovedAt(Instant.now());
        rental = rentalRequestRepository.save(rental);
        notificationService.notifyUser(rental.getTenantId(), NotificationType.RENTAL_REQUEST_APPROVED,
                "Yêu cầu thuê phòng đã được duyệt",
                "Chủ trọ đã đồng ý lịch xem phòng \"" + listing.title() + "\".",
                "/tai-khoan/yeu-cau-thue", null);
        return toResponse(rental);
    }

    @Override
    @Transactional
    public RentalRequestResponse reject(Long requestId, Long ownerId, RentalDecisionRequest request) {
        RentalRequest rental = lockedForOwner(requestId, ownerId);
        requireStatus(rental, RentalRequestStatus.PENDING, RentalRequestStatus.APPROVED);
        ListingGateway.ListingBrief listing = listingGateway.getBrief(rental.getListingId());
        rental.setStatus(RentalRequestStatus.REJECTED);
        rental.setLandlordNote(cleanNote(request));
        rental.setRejectedAt(Instant.now());
        rental = rentalRequestRepository.save(rental);
        notificationService.notifyUser(rental.getTenantId(), NotificationType.RENTAL_REQUEST_REJECTED,
                "Yêu cầu thuê phòng bị từ chối",
                "Chủ trọ đã từ chối yêu cầu đối với phòng \"" + listing.title() + "\".",
                "/tai-khoan/yeu-cau-thue", null);
        return toResponse(rental);
    }

    @Override
    @Transactional
    public RentalRequestResponse confirmRented(Long requestId, Long ownerId) {
        RentalRequest rental = lockedForOwner(requestId, ownerId);
        requireStatus(rental, RentalRequestStatus.APPROVED);
        ListingGateway.ListingBrief listing = listingGateway.getBrief(rental.getListingId());
        if (listing.status() != ListingStatus.ACTIVE) {
            throw new ConflictException(ErrorCode.RENTAL_LISTING_UNAVAILABLE);
        }
        if (rentalRequestRepository.existsByListingIdAndStatusAndDeletedAtIsNull(
                rental.getListingId(), RentalRequestStatus.RENTED)) {
            throw new ConflictException(ErrorCode.RENTAL_LISTING_OCCUPIED);
        }
        if (rentalRequestRepository.existsByTenantIdAndStatusAndDeletedAtIsNull(
                rental.getTenantId(), RentalRequestStatus.RENTED)) {
            throw new ConflictException(ErrorCode.RENTAL_TENANT_ALREADY_RENTING);
        }

        rental.setStatus(RentalRequestStatus.RENTED);
        rental.setRentedAt(Instant.now());
        try {
            rental = rentalRequestRepository.saveAndFlush(rental);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException(ErrorCode.RENTAL_CONCURRENCY_CONFLICT);
        }
        listingGateway.markRented(rental.getListingId(), ownerId);
        closeCompetingRequests(rental, listing);

        notificationService.notifyUser(rental.getTenantId(), NotificationType.RENTAL_CONFIRMED,
                "Đã xác nhận thuê phòng",
                "Bạn đã được xác nhận là người thuê của phòng \"" + listing.title() + "\".",
                "/tai-khoan/yeu-cau-thue", null);
        return toResponse(rental);
    }

    @Override
    @Transactional
    public RentalRequestResponse confirmCheckedOut(Long requestId, Long ownerId) {
        RentalRequest rental = lockedForOwner(requestId, ownerId);
        requireStatus(rental, RentalRequestStatus.RENTED);
        ListingGateway.ListingBrief listing = listingGateway.getBrief(rental.getListingId());
        rental.setStatus(RentalRequestStatus.CHECKED_OUT);
        rental.setCheckedOutAt(Instant.now());
        rental = rentalRequestRepository.saveAndFlush(rental);
        listingGateway.markAvailableAfterCheckout(rental.getListingId(), ownerId);
        notificationService.notifyUser(rental.getTenantId(), NotificationType.RENTAL_CHECKED_OUT,
                "Đã xác nhận trả phòng",
                "Chủ trọ đã xác nhận bạn trả phòng \"" + listing.title()
                        + "\". Bạn vẫn có thể đánh giá phòng này.",
                "/tai-khoan/yeu-cau-thue", null);
        return toResponse(rental);
    }

    private void closeCompetingRequests(RentalRequest confirmed, ListingGateway.ListingBrief listing) {
        Instant now = Instant.now();
        List<RentalRequest> sameListing = rentalRequestRepository
                .findByListingIdAndStatusInAndDeletedAtIsNull(confirmed.getListingId(), WAITING_STATUSES);
        sameListing.stream().filter(r -> !r.getId().equals(confirmed.getId())).forEach(r -> {
            r.setStatus(RentalRequestStatus.REJECTED);
            r.setRejectedAt(now);
            r.setLandlordNote("Phòng đã được cho thuê");
            notificationService.notifyUser(r.getTenantId(), NotificationType.RENTAL_REQUEST_REJECTED,
                    "Phòng đã được cho thuê",
                    "Yêu cầu của bạn cho phòng \"" + listing.title() + "\" đã kết thúc vì phòng đã có người thuê.",
                    "/tai-khoan/yeu-cau-thue", null);
        });
        rentalRequestRepository.saveAll(sameListing);

        List<RentalRequest> sameTenant = rentalRequestRepository
                .findByTenantIdAndStatusInAndDeletedAtIsNull(confirmed.getTenantId(), WAITING_STATUSES);
        sameTenant.stream().filter(r -> !r.getId().equals(confirmed.getId())).forEach(r -> {
            r.setStatus(RentalRequestStatus.CANCELLED);
            r.setCancelledAt(now);
        });
        rentalRequestRepository.saveAll(sameTenant);
    }

    private RentalRequest locked(Long id) {
        return rentalRequestRepository.findAliveByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RENTAL_REQUEST_NOT_FOUND));
    }

    private RentalRequest lockedForOwner(Long id, Long ownerId) {
        RentalRequest rental = locked(id);
        ListingGateway.ListingBrief listing = listingGateway.getBrief(rental.getListingId());
        if (!listing.ownerId().equals(ownerId)) {
            throw new ForbiddenException(ErrorCode.RENTAL_REQUEST_FORBIDDEN);
        }
        return rental;
    }

    private void requireStatus(RentalRequest rental, RentalRequestStatus... allowed) {
        if (!List.of(allowed).contains(rental.getStatus())) {
            throw new BusinessRuleException(ErrorCode.RENTAL_REQUEST_INVALID_STATUS);
        }
    }

    private String cleanNote(RentalDecisionRequest request) {
        return request == null ? null : HtmlSanitizer.stripAllHtml(request.getNote());
    }

    private LocalDate businessToday() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    private PageResponse<RentalRequestResponse> mapPage(Page<RentalRequest> page) {
        Collection<Long> listingIds = page.getContent().stream().map(RentalRequest::getListingId).toList();
        Collection<Long> tenantIds = page.getContent().stream().map(RentalRequest::getTenantId).toList();
        Collection<Long> unitIds = page.getContent().stream().map(RentalRequest::getRentalUnitId).toList();
        Map<Long, ListingGateway.ListingBrief> listings = listingGateway.getBriefs(listingIds);
        Map<Long, UserGateway.UserBrief> tenants = userGateway.getBriefs(tenantIds);
        Map<Long, ListingGateway.RentalUnitBrief> units = listingGateway.getUnitBriefs(unitIds);
        return PageResponse.from(page, r -> toResponse(r, listings.get(r.getListingId()),
                tenants.get(r.getTenantId()), units.get(r.getRentalUnitId())));
    }

    private RentalRequestResponse toResponse(RentalRequest rental) {
        return toResponse(rental, listingGateway.getBrief(rental.getListingId()), userGateway.getBrief(rental.getTenantId()));
    }

    private RentalRequestResponse toResponse(
            RentalRequest rental, ListingGateway.ListingBrief listing, UserGateway.UserBrief tenant) {
        ListingGateway.RentalUnitBrief unit = rental.getRentalUnitId() == null ? null
                : listingGateway.getUnitBriefs(java.util.List.of(rental.getRentalUnitId())).get(rental.getRentalUnitId());
        return toResponse(rental, listing, tenant, unit);
    }

    private RentalRequestResponse toResponse(RentalRequest rental, ListingGateway.ListingBrief listing,
                                             UserGateway.UserBrief tenant, ListingGateway.RentalUnitBrief unit) {
        return RentalRequestResponse.builder()
                .id(rental.getId())
                .listingId(rental.getListingId())
                .rentalUnitId(rental.getRentalUnitId())
                .rentalUnitName(unit == null ? null : unit.name())
                .listingTitle(listing == null ? null : listing.title())
                .listingSlug(listing == null ? null : listing.slug())
                .listingThumbnailUrl(listing == null ? null : listing.thumbnailUrl())
                .listingPrice(listing == null ? null : listing.price())
                .tenantId(rental.getTenantId())
                .tenantName(tenant == null ? null : tenant.fullName())
                .tenantAvatarUrl(tenant == null ? null : tenant.avatarUrl())
                .tenantPhone(tenant == null ? null : tenant.phone())
                .desiredViewingDate(rental.getDesiredViewingDate())
                .status(rental.getStatus())
                .landlordNote(rental.getLandlordNote())
                .approvedAt(rental.getApprovedAt())
                .rejectedAt(rental.getRejectedAt())
                .cancelledAt(rental.getCancelledAt())
                .rentedAt(rental.getRentedAt())
                .checkedOutAt(rental.getCheckedOutAt())
                .createdAt(rental.getCreatedAt())
                .build();
    }
}
