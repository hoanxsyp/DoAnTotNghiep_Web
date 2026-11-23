package com.webtro.modules.listing.service;

import com.webtro.common.enums.ListingStatus;
import com.webtro.common.enums.RentalUnitStatus;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessRuleException;
import com.webtro.exception.ConflictException;
import com.webtro.modules.catalog.entity.Ward;
import com.webtro.modules.catalog.repository.DistrictRepository;
import com.webtro.modules.catalog.repository.ProvinceRepository;
import com.webtro.modules.catalog.repository.WardRepository;
import com.webtro.modules.listing.entity.Listing;
import com.webtro.modules.listing.entity.RentalUnit;
import com.webtro.modules.listing.repository.ListingRepository;
import com.webtro.modules.listing.repository.RentalUnitRepository;
import com.webtro.modules.listing.statemachine.ListingStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Quy tắc RU-2, RU-3, RU-4, RU-6 trong docs/DANH_GIA_THEO_PHONG_DOI_CHIEU.md mục 3.1. */
@ExtendWith(MockitoExtension.class)
class RentalUnitSupportTest {

    private static final long OWNER_ID = 1L;
    private static final long OTHER_OWNER_ID = 2L;
    private static final long WARD_ID = 100L;

    @Mock
    private RentalUnitRepository rentalUnitRepository;
    @Mock
    private ListingRepository listingRepository;
    @Mock
    private ProvinceRepository provinceRepository;
    @Mock
    private DistrictRepository districtRepository;
    @Mock
    private WardRepository wardRepository;
    @Mock
    private ListingCategoryCountPublisher categoryCountPublisher;
    @Mock
    private ListingOwnerStatsPublisher ownerStatsPublisher;

    private RentalUnitSupport support;
    private final Ward ward = Ward.builder().id(WARD_ID).name("Dịch Vọng").build();

    @BeforeEach
    void setUp() {
        support = new RentalUnitSupport(rentalUnitRepository, listingRepository, provinceRepository,
                districtRepository, wardRepository, new ListingStateMachine(),
                categoryCountPublisher, ownerStatsPublisher);
    }

    @Test
    void liveStatusesIncludeRejectedAndExpiredButNotClosed() {
        assertTrue(RentalUnitSupport.LIVE_STATUSES.contains(ListingStatus.REJECTED));
        assertTrue(RentalUnitSupport.LIVE_STATUSES.contains(ListingStatus.EXPIRED));
        assertTrue(RentalUnitSupport.LIVE_STATUSES.contains(ListingStatus.RENTED));
        assertFalse(RentalUnitSupport.LIVE_STATUSES.contains(ListingStatus.CLOSED));
        assertFalse(RentalUnitSupport.LIVE_STATUSES.contains(ListingStatus.DELETED));
    }

    @Test
    void createUnitBlocksSameOwnerDuplicateWrittenDifferently() {
        when(rentalUnitRepository.findByWardAndStatus(WARD_ID, RentalUnitStatus.ACTIVE))
                .thenReturn(List.of(unit(5L, OWNER_ID, "Số 12, Ngõ 5 Cầu Giấy", "Phòng 101", false)));

        ConflictException ex = assertThrows(ConflictException.class, () -> support.createUnit(OWNER_ID, "P101",
                new RentalUnitSupport.UnitAddress(ward, "12 ngo 5 cau giay", "P.101", null, null)));

        assertEquals(ErrorCode.RENTAL_UNIT_DUPLICATE, ex.getErrorCode());
        verify(rentalUnitRepository, never()).save(any());
    }

    @Test
    void createUnitAllowsSameAddressWithDifferentRoomNumber() {
        when(rentalUnitRepository.findByWardAndStatus(WARD_ID, RentalUnitStatus.ACTIVE))
                .thenReturn(List.of(unit(5L, OWNER_ID, "Số 12 ngõ 5", "101", false)));
        when(rentalUnitRepository.save(any(RentalUnit.class))).thenAnswer(inv -> inv.getArgument(0));

        RentalUnit created = support.createUnit(OWNER_ID, "P102",
                new RentalUnitSupport.UnitAddress(ward, "Số 12 ngõ 5", "102", null, null));

        assertEquals("102", created.getUnitLabel());
        assertEquals(WARD_ID, created.getWardId());
    }

    @Test
    void otherOwnerDuplicateIsOnlyFlagged() {
        when(rentalUnitRepository.findByWardAndStatus(WARD_ID, RentalUnitStatus.ACTIVE))
                .thenReturn(List.of(unit(5L, OTHER_OWNER_ID, "Số 12 ngõ 5", null, false)));

        assertTrue(support.hasOtherOwnerDuplicate(OWNER_ID, WARD_ID, "so 12 ngo 5", null));
        assertFalse(support.hasOtherOwnerDuplicate(OTHER_OWNER_ID, WARD_ID, "so 12 ngo 5", null));
    }

    @Test
    void lockForNewListingRejectsUnitThatStillHasALiveListing() {
        RentalUnit u = unit(5L, OWNER_ID, "Số 12 ngõ 5", null, false);
        when(rentalUnitRepository.findAliveByIdForUpdate(5L)).thenReturn(Optional.of(u));
        Listing live = Listing.builder().id(9L).title("Tin đang hiển thị").status(ListingStatus.EXPIRED).build();
        when(listingRepository.findByRentalUnitAndStatuses(eq(5L), anyCollection())).thenReturn(List.of(live));

        ConflictException ex = assertThrows(ConflictException.class, () -> support.lockForNewListing(5L, OWNER_ID));
        assertEquals(ErrorCode.RENTAL_UNIT_HAS_LIVE_LISTING, ex.getErrorCode());
    }

    @Test
    void lockForNewListingRejectsArchivedUnit() {
        RentalUnit u = unit(5L, OWNER_ID, "Số 12 ngõ 5", null, false);
        u.setStatus(RentalUnitStatus.ARCHIVED);
        when(rentalUnitRepository.findAliveByIdForUpdate(5L)).thenReturn(Optional.of(u));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> support.lockForNewListing(5L, OWNER_ID));
        assertEquals(ErrorCode.RENTAL_UNIT_ARCHIVED, ex.getErrorCode());
    }

    @Test
    void lockedAddressCannotBeChangedByOwner() {
        RentalUnit u = unit(5L, OWNER_ID, "Số 12 ngõ 5", "101", true);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> support.changeAddress(u,
                new RentalUnitSupport.UnitAddress(ward, "Số 99 phố khác", "101", null, null), false, null));

        assertEquals(ErrorCode.RENTAL_UNIT_ADDRESS_LOCKED, ex.getErrorCode());
        verify(rentalUnitRepository, never()).save(any());
    }

    @Test
    void changingAddressSyncsAllListingsAndSendsActiveOnesBackToModeration() {
        RentalUnit u = unit(5L, OWNER_ID, "Số 12 ngõ 5", null, false);
        Listing active = Listing.builder().id(9L).rentalUnitId(5L).status(ListingStatus.ACTIVE)
                .wardId(WARD_ID).addressDetail("Số 12 ngõ 5").build();
        Listing closed = Listing.builder().id(8L).rentalUnitId(5L).status(ListingStatus.CLOSED)
                .wardId(WARD_ID).addressDetail("Số 12 ngõ 5").build();
        when(listingRepository.findAllByRentalUnit(5L)).thenReturn(List.of(active, closed));

        boolean changed = support.changeAddress(u,
                new RentalUnitSupport.UnitAddress(ward, "Số 14 ngõ 5", null, null, null), false, null);

        assertTrue(changed);
        assertEquals("Số 14 ngõ 5", active.getAddressDetail());
        assertEquals("Số 14 ngõ 5", closed.getAddressDetail());
        assertEquals(ListingStatus.PENDING, active.getStatus());
        assertEquals(ListingStatus.CLOSED, closed.getStatus());
    }

    @Test
    void changingOnlyRoomNumberDoesNotSendActiveListingBackToModeration() {
        RentalUnit u = unit(5L, OWNER_ID, "Số 12 ngõ 5", null, false);

        boolean changed = support.changeAddress(u,
                new RentalUnitSupport.UnitAddress(ward, "Số 12 ngõ 5", "P101", null, null), false, null);

        assertTrue(changed);
        assertEquals("P101", u.getUnitLabel());
        // Không đồng bộ/duyệt lại tin nào vì địa chỉ hiển thị trên tin không đổi.
        verify(listingRepository, never()).findAllByRentalUnit(5L);
    }

    @Test
    void sameAddressWithoutCoordinatesIsNotAChangeAndKeepsCoordinates() {
        RentalUnit u = unit(5L, OWNER_ID, "Số 12 ngõ 5", null, false);
        u.setLocationSource(com.webtro.common.enums.LocationSource.OWNER);

        boolean changed = support.changeAddress(u,
                new RentalUnitSupport.UnitAddress(ward, "Số 12 ngõ 5", null, null, null), false, null);

        assertFalse(changed);
        assertEquals(new BigDecimal("21.0300000"), u.getLatitude());
        verify(rentalUnitRepository, never()).save(any());
    }

    @Test
    void adminCanChangeLockedAddress() {
        RentalUnit u = unit(5L, OWNER_ID, "Số 12 ngõ 5", null, true);
        when(listingRepository.findAllByRentalUnit(5L)).thenReturn(List.of());

        assertTrue(support.changeAddress(u,
                new RentalUnitSupport.UnitAddress(ward, "Số 12A ngõ 5", null, null, null), true, null));
        assertEquals("Số 12A ngõ 5", u.getAddressDetail());
    }

    private RentalUnit unit(long id, long ownerId, String address, String label, boolean locked) {
        RentalUnit u = RentalUnit.builder()
                .id(id)
                .ownerId(ownerId)
                .name("Phòng " + id)
                .unitLabel(label)
                .wardId(WARD_ID)
                .addressDetail(address)
                .latitude(new BigDecimal("21.0300000"))
                .longitude(new BigDecimal("105.7800000"))
                .status(RentalUnitStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(u, "addressLocked", locked);
        return u;
    }
}
