package com.webtro.modules.rental.service.impl;

import com.webtro.common.enums.ListingStatus;
import com.webtro.common.enums.NotificationType;
import com.webtro.common.enums.RentalRequestStatus;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessException;
import com.webtro.exception.BusinessRuleException;
import com.webtro.exception.ConflictException;
import com.webtro.exception.ForbiddenException;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.interaction.spi.ListingGateway;
import com.webtro.modules.interaction.spi.UserGateway;
import com.webtro.modules.notification.service.NotificationService;
import com.webtro.modules.rental.dto.request.CreateRentalRequest;
import com.webtro.modules.rental.dto.request.UpdateViewingDateRequest;
import com.webtro.modules.rental.entity.RentalRequest;
import com.webtro.modules.rental.repository.RentalRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UT luồng yêu cầu thuê phòng nhìn từ phía người thuê (TENANT): gửi, xem, đổi ngày, hủy, và các
 * thông báo người thuê nhận được khi chủ trọ duyệt/từ chối/xác nhận thuê/xác nhận trả phòng.
 * Mã test khớp tài liệu docs/11_KIEM_THU_LUONG_TENANT.md (UT-RR-xx).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RentalRequestServiceImplTenantTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final long REQUEST_ID = 10L;
    private static final long LISTING_ID = 20L;
    private static final long TENANT_ID = 30L;
    private static final long OTHER_TENANT_ID = 31L;
    private static final long OWNER_ID = 40L;
    private static final long UNIT_ID = 900L;

    @Mock
    private RentalRequestRepository repository;
    @Mock
    private ListingGateway listingGateway;
    @Mock
    private UserGateway userGateway;
    @Mock
    private NotificationService notificationService;

    private RentalRequestServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RentalRequestServiceImpl(repository, listingGateway, userGateway, notificationService);
        when(userGateway.getBrief(TENANT_ID)).thenReturn(
                new UserGateway.UserBrief(TENANT_ID, "Người thuê A", null, "0912345678", null));
        when(listingGateway.getUnitBriefs(anyCollection())).thenReturn(Map.of());
        when(userGateway.hasLandlordProfile(OWNER_ID)).thenReturn(true);
        when(repository.saveAndFlush(any(RentalRequest.class))).thenAnswer(inv -> {
            RentalRequest r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(REQUEST_ID);
            }
            return r;
        });
        when(repository.save(any(RentalRequest.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ================================================================== gửi yêu cầu

    @Test
    @DisplayName("UT-RR-01 Gửi yêu cầu hợp lệ → PENDING, gắn phòng của tin, báo chủ trọ")
    void createValidRequest() {
        stubListing(ListingStatus.ACTIVE, true);
        when(repository.existsByListingIdAndTenantIdAndStatusInAndDeletedAtIsNull(
                eq(LISTING_ID), eq(TENANT_ID), anyCollection())).thenReturn(false);

        var response = service.create(createRequest(today().plusDays(2)), TENANT_ID);

        ArgumentCaptor<RentalRequest> saved = ArgumentCaptor.forClass(RentalRequest.class);
        verify(repository).saveAndFlush(saved.capture());
        assertEquals(RentalRequestStatus.PENDING, saved.getValue().getStatus());
        assertEquals(UNIT_ID, saved.getValue().getRentalUnitId());
        assertEquals(TENANT_ID, saved.getValue().getTenantId());
        assertEquals(RentalRequestStatus.PENDING, response.getStatus());
        assertEquals("Phòng test", response.getListingTitle());
        verify(notificationService).notifyUser(eq(OWNER_ID), eq(NotificationType.RENTAL_REQUEST_CREATED),
                anyString(), anyString(), eq("/quan-ly/yeu-cau-thue"), eq(null));
    }

    @Test
    @DisplayName("UT-RR-02 Ngày xem = hôm nay (giờ VN) vẫn hợp lệ")
    void createAllowsToday() {
        stubListing(ListingStatus.ACTIVE, true);

        var response = service.create(createRequest(today()), TENANT_ID);

        assertEquals(today(), response.getDesiredViewingDate());
    }

    @Test
    @DisplayName("UT-RR-03 Gửi yêu cầu vào tin của chính mình → CONTACT_FORBIDDEN_SELF")
    void createOnOwnListingIsRejected() {
        stubListingOwnedBy(TENANT_ID, ListingStatus.ACTIVE, true);

        BusinessException ex = assertThrows(BusinessRuleException.class,
                () -> service.create(createRequest(today().plusDays(1)), TENANT_ID));

        assertEquals(ErrorCode.CONTACT_FORBIDDEN_SELF, ex.getErrorCode());
        verify(repository, never()).saveAndFlush(any());
    }

    @ParameterizedTest(name = "UT-RR-04 Tin trạng thái {0} → RENTAL_LISTING_UNAVAILABLE")
    @EnumSource(value = ListingStatus.class,
            names = {"DRAFT", "PENDING", "REJECTED", "HIDDEN", "LOCKED", "CLOSED", "EXPIRED", "RENTED", "NEED_REVIEW"})
    void createOnUnavailableListingIsRejected(ListingStatus status) {
        // NEED_REVIEW vẫn hiển thị công khai nhưng không nhận yêu cầu thuê (chỉ ACTIVE).
        stubListing(status, status == ListingStatus.NEED_REVIEW);

        BusinessException ex = assertThrows(ConflictException.class,
                () -> service.create(createRequest(today().plusDays(1)), TENANT_ID));

        assertEquals(ErrorCode.RENTAL_LISTING_UNAVAILABLE, ex.getErrorCode());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("UT-RR-04b Tin ở ghép do tenant đăng (không có hồ sơ chủ trọ) → RENTAL_NOT_SUPPORTED_FOR_ROOMMATE")
    void createOnTenantRoommateListingIsRejected() {
        stubListingOwnedBy(OTHER_TENANT_ID, ListingStatus.ACTIVE, true);
        when(userGateway.hasLandlordProfile(OTHER_TENANT_ID)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessRuleException.class,
                () -> service.create(createRequest(today().plusDays(1)), TENANT_ID));

        assertEquals(ErrorCode.RENTAL_NOT_SUPPORTED_FOR_ROOMMATE, ex.getErrorCode());
        verify(repository, never()).saveAndFlush(any());
        verify(notificationService, never()).notifyUser(any(), any(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("UT-RR-05 Ngày xem ở quá khứ → RENTAL_VIEWING_DATE_INVALID")
    void createWithPastViewingDateIsRejected() {
        stubListing(ListingStatus.ACTIVE, true);

        BusinessException ex = assertThrows(BusinessRuleException.class,
                () -> service.create(createRequest(today().minusDays(1)), TENANT_ID));

        assertEquals(ErrorCode.RENTAL_VIEWING_DATE_INVALID, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-RR-06 Đã có yêu cầu đang mở cho tin → RENTAL_REQUEST_ALREADY_OPEN")
    void createDuplicateOpenRequestIsRejected() {
        stubListing(ListingStatus.ACTIVE, true);
        when(repository.existsByListingIdAndTenantIdAndStatusInAndDeletedAtIsNull(
                eq(LISTING_ID), eq(TENANT_ID), anyCollection())).thenReturn(true);

        BusinessException ex = assertThrows(ConflictException.class,
                () -> service.create(createRequest(today().plusDays(1)), TENANT_ID));

        assertEquals(ErrorCode.RENTAL_REQUEST_ALREADY_OPEN, ex.getErrorCode());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("UT-RR-07 Hai request gửi đồng thời đụng unique key → vẫn trả ALREADY_OPEN, không 500")
    void createConcurrentDuplicateMapsToConflict() {
        stubListing(ListingStatus.ACTIVE, true);
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk"));

        BusinessException ex = assertThrows(ConflictException.class,
                () -> service.create(createRequest(today().plusDays(1)), TENANT_ID));

        assertEquals(ErrorCode.RENTAL_REQUEST_ALREADY_OPEN, ex.getErrorCode());
        verify(notificationService, never()).notifyUser(any(), any(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("UT-RR-08 Tin không tồn tại/đã xóa → LISTING_NOT_FOUND")
    void createOnMissingListing() {
        when(listingGateway.getBrief(LISTING_ID))
                .thenThrow(new ResourceNotFoundException(ErrorCode.LISTING_NOT_FOUND));

        BusinessException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.create(createRequest(today().plusDays(1)), TENANT_ID));

        assertEquals(ErrorCode.LISTING_NOT_FOUND, ex.getErrorCode());
    }

    // ================================================================== xem danh sách

    @Test
    @DisplayName("UT-RR-09 Danh sách của tôi: không lọc → truy vấn theo tenant; có lọc → theo tenant + status")
    void listMineUsesTenantScopedQueries() {
        Pageable pageable = PageRequest.of(0, 20);
        RentalRequest r = rental(RentalRequestStatus.PENDING, today().plusDays(2), TENANT_ID);
        Page<RentalRequest> page = new PageImpl<>(List.of(r), pageable, 1);
        when(repository.findByTenantIdAndDeletedAtIsNull(TENANT_ID, pageable)).thenReturn(page);
        when(repository.findByTenantIdAndStatusAndDeletedAtIsNull(TENANT_ID, RentalRequestStatus.APPROVED, pageable))
                .thenReturn(Page.empty(pageable));
        when(listingGateway.getBriefs(anyCollection())).thenReturn(Map.of(LISTING_ID, brief(OWNER_ID, ListingStatus.ACTIVE, true)));
        when(userGateway.getBriefs(anyCollection())).thenReturn(Map.of());

        var all = service.listMine(TENANT_ID, null, pageable);
        var approved = service.listMine(TENANT_ID, RentalRequestStatus.APPROVED, pageable);

        assertEquals(1, all.getItems().size());
        assertEquals("Phòng test", all.getItems().get(0).getListingTitle());
        assertEquals(0, approved.getItems().size());
        verify(repository).findByTenantIdAndDeletedAtIsNull(TENANT_ID, pageable);
        verify(repository).findByTenantIdAndStatusAndDeletedAtIsNull(TENANT_ID, RentalRequestStatus.APPROVED, pageable);
    }

    // ================================================================== hủy

    @Test
    @DisplayName("UT-RR-10 Hủy yêu cầu PENDING kể cả khi ngày xem là hôm nay")
    void cancelPendingAnyTime() {
        RentalRequest r = rental(RentalRequestStatus.PENDING, today(), TENANT_ID);
        stubLocked(r);
        stubListing(ListingStatus.ACTIVE, true);

        var response = service.cancel(REQUEST_ID, TENANT_ID);

        assertEquals(RentalRequestStatus.CANCELLED, response.getStatus());
        assertNotNull(r.getCancelledAt());
        verify(notificationService).notifyUser(eq(OWNER_ID), eq(NotificationType.RENTAL_REQUEST_CANCELLED),
                anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("UT-RR-11 Hủy yêu cầu của người khác → RENTAL_REQUEST_FORBIDDEN")
    void cancelSomeoneElsesRequestIsForbidden() {
        stubLocked(rental(RentalRequestStatus.PENDING, today().plusDays(3), OTHER_TENANT_ID));

        BusinessException ex = assertThrows(ForbiddenException.class, () -> service.cancel(REQUEST_ID, TENANT_ID));

        assertEquals(ErrorCode.RENTAL_REQUEST_FORBIDDEN, ex.getErrorCode());
        verify(repository, never()).save(any());
    }

    @ParameterizedTest(name = "UT-RR-12 Hủy khi trạng thái {0} → RENTAL_REQUEST_INVALID_STATUS")
    @EnumSource(value = RentalRequestStatus.class, names = {"REJECTED", "CANCELLED", "RENTED", "CHECKED_OUT"})
    void cancelInTerminalStatusIsRejected(RentalRequestStatus status) {
        stubLocked(rental(status, today().plusDays(5), TENANT_ID));

        BusinessException ex = assertThrows(BusinessRuleException.class, () -> service.cancel(REQUEST_ID, TENANT_ID));

        assertEquals(ErrorCode.RENTAL_REQUEST_INVALID_STATUS, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-RR-13 Hủy APPROVED khi ngày xem là hôm nay → RENTAL_CANCELLATION_DEADLINE_PASSED")
    void cancelApprovedOnViewingDayIsRejected() {
        stubLocked(rental(RentalRequestStatus.APPROVED, today(), TENANT_ID));

        BusinessException ex = assertThrows(BusinessRuleException.class, () -> service.cancel(REQUEST_ID, TENANT_ID));

        assertEquals(ErrorCode.RENTAL_CANCELLATION_DEADLINE_PASSED, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-RR-14 Hủy yêu cầu không tồn tại → RENTAL_REQUEST_NOT_FOUND")
    void cancelMissingRequest() {
        when(repository.findAliveByIdForUpdate(REQUEST_ID)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(ResourceNotFoundException.class, () -> service.cancel(REQUEST_ID, TENANT_ID));

        assertEquals(ErrorCode.RENTAL_REQUEST_NOT_FOUND, ex.getErrorCode());
    }

    // ================================================================== đổi ngày xem

    @Test
    @DisplayName("UT-RR-15 Đổi ngày xem của yêu cầu người khác → RENTAL_REQUEST_FORBIDDEN")
    void updateViewingDateOfOthersIsForbidden() {
        stubLocked(rental(RentalRequestStatus.PENDING, today().plusDays(3), OTHER_TENANT_ID));

        assertThrows(ForbiddenException.class,
                () -> service.updateViewingDate(REQUEST_ID, TENANT_ID, viewing(today().plusDays(4))));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("UT-RR-16 Đổi ngày xem sang quá khứ → RENTAL_VIEWING_DATE_INVALID")
    void updateViewingDateToPastIsRejected() {
        stubLocked(rental(RentalRequestStatus.PENDING, today().plusDays(3), TENANT_ID));

        BusinessException ex = assertThrows(BusinessRuleException.class,
                () -> service.updateViewingDate(REQUEST_ID, TENANT_ID, viewing(today().minusDays(1))));

        assertEquals(ErrorCode.RENTAL_VIEWING_DATE_INVALID, ex.getErrorCode());
    }

    // ================================================================== thông báo tenant nhận

    @Test
    @DisplayName("UT-RR-17 Chủ trọ duyệt → tenant nhận RENTAL_REQUEST_APPROVED, link /tai-khoan/yeu-cau-thue")
    void approveNotifiesTenant() {
        stubLocked(rental(RentalRequestStatus.PENDING, today().plusDays(3), TENANT_ID));
        stubListing(ListingStatus.ACTIVE, true);

        var response = service.approve(REQUEST_ID, OWNER_ID, null);

        assertEquals(RentalRequestStatus.APPROVED, response.getStatus());
        verify(notificationService).notifyUser(eq(TENANT_ID), eq(NotificationType.RENTAL_REQUEST_APPROVED),
                anyString(), anyString(), eq("/tai-khoan/yeu-cau-thue"), eq(null));
    }

    @Test
    @DisplayName("UT-RR-18 Chủ trọ từ chối → tenant nhận RENTAL_REQUEST_REJECTED")
    void rejectNotifiesTenant() {
        stubLocked(rental(RentalRequestStatus.APPROVED, today().plusDays(3), TENANT_ID));
        stubListing(ListingStatus.ACTIVE, true);

        var response = service.reject(REQUEST_ID, OWNER_ID, null);

        assertEquals(RentalRequestStatus.REJECTED, response.getStatus());
        verify(notificationService).notifyUser(eq(TENANT_ID), eq(NotificationType.RENTAL_REQUEST_REJECTED),
                anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("UT-RR-19 Xác nhận thuê: tenant nhận RENTAL_CONFIRMED; yêu cầu khác cùng tin bị REJECTED + báo; "
            + "yêu cầu chờ khác của tenant bị CANCELLED")
    void confirmRentedClosesCompetingRequests() {
        RentalRequest confirmed = rental(RentalRequestStatus.APPROVED, today().plusDays(1), TENANT_ID);
        stubLocked(confirmed);
        stubListing(ListingStatus.ACTIVE, true);
        RentalRequest competitor = rental(RentalRequestStatus.PENDING, today().plusDays(2), OTHER_TENANT_ID);
        competitor.setId(11L);
        RentalRequest myOther = rental(RentalRequestStatus.APPROVED, today().plusDays(2), TENANT_ID);
        myOther.setId(12L);
        myOther.setListingId(99L);
        when(repository.findByListingIdAndStatusInAndDeletedAtIsNull(eq(LISTING_ID), anyCollection()))
                .thenReturn(new ArrayList<>(List.of(confirmed, competitor)));
        when(repository.findByTenantIdAndStatusInAndDeletedAtIsNull(eq(TENANT_ID), anyCollection()))
                .thenReturn(new ArrayList<>(List.of(confirmed, myOther)));

        var response = service.confirmRented(REQUEST_ID, OWNER_ID);

        assertEquals(RentalRequestStatus.RENTED, response.getStatus());
        assertEquals(RentalRequestStatus.REJECTED, competitor.getStatus());
        assertEquals(RentalRequestStatus.CANCELLED, myOther.getStatus());
        verify(listingGateway).markRented(LISTING_ID, OWNER_ID);
        verify(notificationService).notifyUser(eq(TENANT_ID), eq(NotificationType.RENTAL_CONFIRMED),
                anyString(), anyString(), any(), any());
        verify(notificationService).notifyUser(eq(OTHER_TENANT_ID), eq(NotificationType.RENTAL_REQUEST_REJECTED),
                anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("UT-RR-20 Tenant đang thuê phòng khác → không thể được xác nhận thuê thêm")
    void confirmRentedRejectsTenantAlreadyRenting() {
        stubLocked(rental(RentalRequestStatus.APPROVED, today().plusDays(1), TENANT_ID));
        stubListing(ListingStatus.ACTIVE, true);
        when(repository.existsByTenantIdAndStatusAndDeletedAtIsNull(TENANT_ID, RentalRequestStatus.RENTED))
                .thenReturn(true);

        BusinessException ex = assertThrows(ConflictException.class,
                () -> service.confirmRented(REQUEST_ID, OWNER_ID));

        assertEquals(ErrorCode.RENTAL_TENANT_ALREADY_RENTING, ex.getErrorCode());
        verify(listingGateway, never()).markRented(any(), any());
    }

    @Test
    @DisplayName("UT-RR-21 Xác nhận trả phòng → CHECKED_OUT, tin mở lại, tenant nhận RENTAL_CHECKED_OUT")
    void confirmCheckedOutNotifiesTenant() {
        stubLocked(rental(RentalRequestStatus.RENTED, today().minusDays(30), TENANT_ID));
        stubListing(ListingStatus.RENTED, false);

        var response = service.confirmCheckedOut(REQUEST_ID, OWNER_ID);

        assertEquals(RentalRequestStatus.CHECKED_OUT, response.getStatus());
        assertNotNull(response.getCheckedOutAt());
        verify(listingGateway).markAvailableAfterCheckout(LISTING_ID, OWNER_ID);
        verify(notificationService).notifyUser(eq(TENANT_ID), eq(NotificationType.RENTAL_CHECKED_OUT),
                anyString(), anyString(), any(), any());
    }

    // ================================================================== helper

    private void stubListing(ListingStatus status, boolean publiclyVisible) {
        stubListingOwnedBy(OWNER_ID, status, publiclyVisible);
    }

    private void stubListingOwnedBy(long ownerId, ListingStatus status, boolean publiclyVisible) {
        when(listingGateway.getBrief(LISTING_ID)).thenReturn(brief(ownerId, status, publiclyVisible));
    }

    private ListingGateway.ListingBrief brief(long ownerId, ListingStatus status, boolean publiclyVisible) {
        return new ListingGateway.ListingBrief(LISTING_ID, ownerId, "Phòng test", "phong-test", null,
                BigDecimal.valueOf(3_000_000), BigDecimal.valueOf(25), "Hà Nội", status, publiclyVisible,
                BigDecimal.ZERO, 0, UNIT_ID, null);
    }

    private void stubLocked(RentalRequest r) {
        when(repository.findAliveByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(r));
    }

    private RentalRequest rental(RentalRequestStatus status, LocalDate viewingDate, long tenantId) {
        return RentalRequest.builder()
                .id(REQUEST_ID)
                .listingId(LISTING_ID)
                .rentalUnitId(UNIT_ID)
                .tenantId(tenantId)
                .desiredViewingDate(viewingDate)
                .status(status)
                .build();
    }

    private CreateRentalRequest createRequest(LocalDate date) {
        CreateRentalRequest r = new CreateRentalRequest();
        r.setListingId(LISTING_ID);
        r.setDesiredViewingDate(date);
        return r;
    }

    private UpdateViewingDateRequest viewing(LocalDate date) {
        UpdateViewingDateRequest r = new UpdateViewingDateRequest();
        r.setDesiredViewingDate(date);
        return r;
    }

    private LocalDate today() {
        return LocalDate.now(BUSINESS_ZONE);
    }
}
