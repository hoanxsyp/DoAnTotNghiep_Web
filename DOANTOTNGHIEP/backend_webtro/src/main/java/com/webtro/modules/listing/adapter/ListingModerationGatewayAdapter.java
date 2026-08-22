package com.webtro.modules.listing.adapter;

import com.webtro.common.enums.ListingStatus;
import com.webtro.common.enums.ReportSeverity;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.catalog.entity.District;
import com.webtro.modules.catalog.entity.Province;
import com.webtro.modules.catalog.entity.Ward;
import com.webtro.modules.catalog.repository.WardRepository;
import com.webtro.modules.listing.entity.Listing;
import com.webtro.modules.listing.repository.ListingImageRepository;
import com.webtro.modules.listing.repository.ListingRepository;
import com.webtro.modules.listing.service.ListingCategoryCountPublisher;
import com.webtro.modules.listing.service.ListingOwnerStatsPublisher;
import com.webtro.modules.listing.statemachine.ListingEvent;
import com.webtro.modules.listing.statemachine.ListingStateMachine;
import com.webtro.modules.moderation.spi.ListingModerationGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Adapter hiện thực {@link ListingModerationGateway} (SPI do module {@code moderation} sở hữu) —
 * canonical luật 4. Mọi đổi trạng thái đi qua {@link ListingStateMachine} (cổng duy nhất, §5.1).
 */
@Component
@RequiredArgsConstructor
public class ListingModerationGatewayAdapter implements ListingModerationGateway {

    private final ListingRepository listingRepository;
    private final ListingImageRepository imageRepository;
    private final WardRepository wardRepository;
    private final ListingStateMachine stateMachine;
    private final ListingCategoryCountPublisher categoryCountPublisher;
    private final ListingOwnerStatsPublisher ownerStatsPublisher;

    @Override
    @Transactional(readOnly = true)
    public ListingRef getRef(Long listingId) {
        return listingRepository.findAliveById(listingId)
                .map(this::toRef)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LISTING_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ListingRef> findRef(Long listingId) {
        return listingRepository.findAliveById(listingId).map(this::toRef);
    }

    @Override
    @Transactional
    public void flagNeedReview(Long listingId, String reason) {
        listingRepository.findAliveById(listingId).ifPresent(l -> {
            // ACTIVE → NEED_REVIEW; không ở trạng thái cho phép thì bỏ qua (không lỗi).
            if (!stateMachine.canTransition(l.getStatus(), ListingEvent.FLAG_NEED_REVIEW)) {
                return;
            }
            ListingCategoryCountPublisher.Snapshot categoryCountBefore = categoryCountPublisher.snapshot(l);
            ListingOwnerStatsPublisher.Snapshot ownerStatsBefore = ownerStatsPublisher.snapshot(l);
            l.setStatus(stateMachine.transition(l.getStatus(), ListingEvent.FLAG_NEED_REVIEW));
            l.setNeedReviewCount(nz(l.getNeedReviewCount()) + 1);
            l.setLastNeedReviewAt(Instant.now());
            listingRepository.save(l);
            categoryCountPublisher.publishIfChanged(categoryCountBefore, l);
            ownerStatsPublisher.publishIfChanged(ownerStatsBefore, l);
        });
    }

    @Override
    @Transactional
    public void clearNeedReview(Long listingId, Long moderatorId, String reason) {
        listingRepository.findAliveById(listingId).ifPresent(l -> {
            // NEED_REVIEW → ACTIVE; không làm gì nếu tin không ở NEED_REVIEW.
            if (!stateMachine.canTransition(l.getStatus(), ListingEvent.CLEAR_NEED_REVIEW)) {
                return;
            }
            ListingCategoryCountPublisher.Snapshot categoryCountBefore = categoryCountPublisher.snapshot(l);
            ListingOwnerStatsPublisher.Snapshot ownerStatsBefore = ownerStatsPublisher.snapshot(l);
            l.setStatus(stateMachine.transition(l.getStatus(), ListingEvent.CLEAR_NEED_REVIEW));
            listingRepository.save(l);
            categoryCountPublisher.publishIfChanged(categoryCountBefore, l);
            ownerStatsPublisher.publishIfChanged(ownerStatsBefore, l);
        });
    }

    @Override
    @Transactional
    public ListingStatus hideByModeration(Long listingId, Long moderatorId, String reason) {
        Listing l = listingRepository.findAliveById(listingId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LISTING_NOT_FOUND));
        ListingStatus before = l.getStatus();
        ListingCategoryCountPublisher.Snapshot categoryCountBefore = categoryCountPublisher.snapshot(l);
        ListingOwnerStatsPublisher.Snapshot ownerStatsBefore = ownerStatsPublisher.snapshot(l);
        // ACTIVE/NEED_REVIEW → HIDDEN, ghi auto_hidden_at để chủ trọ không tự bật lại.
        l.setStatus(stateMachine.transition(before, ListingEvent.AUTO_HIDE_BY_SYSTEM));
        l.setAutoHiddenAt(Instant.now());
        l.setAutoHideReason(trim(reason, 500));
        listingRepository.save(l);
        categoryCountPublisher.publishIfChanged(categoryCountBefore, l);
        ownerStatsPublisher.publishIfChanged(ownerStatsBefore, l);
        return before;
    }

    @Override
    @Transactional
    public ListingStatus lock(Long listingId, Long adminId, ReportSeverity severity, String reason) {
        Listing l = listingRepository.findAliveById(listingId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LISTING_NOT_FOUND));
        ListingStatus before = l.getStatus();
        ListingCategoryCountPublisher.Snapshot categoryCountBefore = categoryCountPublisher.snapshot(l);
        ListingOwnerStatsPublisher.Snapshot ownerStatsBefore = ownerStatsPublisher.snapshot(l);
        // → LOCKED, bắt buộc lý do + mức độ.
        l.setStatus(stateMachine.transition(before, ListingEvent.LOCK));
        l.setLockReason(trim(reason, 500));
        l.setLockSeverity(severity);
        listingRepository.save(l);
        categoryCountPublisher.publishIfChanged(categoryCountBefore, l);
        ownerStatsPublisher.publishIfChanged(ownerStatsBefore, l);
        return before;
    }

    @Override
    @Transactional(readOnly = true)
    public long countLockedListingsSince(Long ownerId, Instant since) {
        return listingRepository.countLockedListingsSince(ownerId, since);
    }

    // ==================================================================
    //  Helper
    // ==================================================================

    private ListingRef toRef(Listing l) {
        return new ListingRef(
                l.getId(),
                l.getOwnerId(),
                l.getTitle(),
                l.getSlug(),
                l.getStatus(),
                l.getPrice(),
                l.getArea(),
                shortAddress(l),
                thumbnailOf(l.getId()));
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
        LocationParts location = locationOf(l.getWardId());
        return joinNonBlank(
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

    private static String joinNonBlank(String... parts) {
        return java.util.Arrays.stream(parts)
                .filter(p -> p != null && !p.isBlank())
                .reduce((a, b) -> a + ", " + b)
                .orElse(null);
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
