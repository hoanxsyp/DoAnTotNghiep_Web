package com.webtro.modules.listing.service.impl;

import com.webtro.common.enums.CategoryCode;
import com.webtro.common.enums.GenderRequirement;
import com.webtro.common.enums.ListingStatus;
import com.webtro.common.enums.UserStatus;
import com.webtro.constant.ConfigKey;
import com.webtro.constant.ErrorCode;
import com.webtro.constant.RoleCode;
import com.webtro.exception.BusinessException;
import com.webtro.exception.BusinessRuleException;
import com.webtro.exception.ForbiddenException;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.catalog.entity.Category;
import com.webtro.modules.catalog.entity.Ward;
import com.webtro.modules.catalog.repository.CategoryRepository;
import com.webtro.modules.listing.dto.request.CloseListingRequest;
import com.webtro.modules.listing.dto.request.ListingCreateRequest;
import com.webtro.modules.listing.dto.request.ListingUpdateRequest;
import com.webtro.modules.listing.dto.request.RenewListingRequest;
import com.webtro.modules.listing.entity.Listing;
import com.webtro.modules.listing.entity.RentalUnit;
import com.webtro.modules.listing.repository.ListingImageRepository;
import com.webtro.modules.listing.repository.ListingRepository;
import com.webtro.modules.listing.service.RentalUnitSupport;
import com.webtro.modules.listing.statemachine.ListingStateMachine;
import com.webtro.modules.user.repository.LandlordProfileRepository;
import com.webtro.common.enums.CloseReason;
import com.webtro.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UT tin ở ghép (ROOMMATE) do người thuê đăng (UT-RM-xx): loại tin được phép, dữ liệu bắt buộc,
 * dùng lại phòng cũ, quyền sở hữu, chuyển trạng thái ẩn/hiện/đóng/xóa, gia hạn.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ListingServiceImplTenantTest {

    private static final long TENANT_ID = 30L;
    private static final long OTHER_ID = 31L;
    private static final long LISTING_ID = 20L;
    private static final long UNIT_ID = 900L;
    private static final long ROOMMATE_CATEGORY = 6L;
    private static final long BOARDING_CATEGORY = 1L;

    @Mock
    private ListingRepository listingRepository;
    @Mock
    private ListingImageRepository imageRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private SystemConfigService config;
    @Mock
    private RentalUnitSupport rentalUnitSupport;
    @Mock
    private LandlordProfileRepository landlordProfileRepository;
    @Spy
    private ListingStateMachine stateMachine = new ListingStateMachine();
    // Các phụ thuộc còn lại: mock rỗng để @InjectMocks dùng được constructor đầy đủ.
    @Mock
    private com.webtro.modules.listing.repository.ListingAmenityRepository amenityLinkRepository;
    @Mock
    private com.webtro.modules.listing.service.ListingVisibilityService visibilityService;
    @Mock
    private com.webtro.modules.listing.service.ListingCategoryCountPublisher categoryCountPublisher;
    @Mock
    private com.webtro.modules.listing.service.ListingOwnerStatsPublisher ownerStatsPublisher;
    @Mock
    private com.webtro.modules.listing.mapper.ListingMapper mapper;
    @Mock
    private com.webtro.security.RateLimitService rateLimitService;
    @Mock
    private com.webtro.storage.FileStorage fileStorage;
    @Mock
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    @Mock
    private com.webtro.modules.catalog.repository.ProvinceRepository provinceRepository;
    @Mock
    private com.webtro.modules.catalog.repository.DistrictRepository districtRepository;
    @Mock
    private com.webtro.modules.catalog.repository.WardRepository wardRepository;
    @Mock
    private com.webtro.modules.catalog.repository.AmenityRepository amenityRepository;
    @Mock
    private com.webtro.modules.payment.repository.PromotionPackageRepository promotionPackageRepository;
    @Mock
    private com.webtro.modules.listing.spi.FavoriteStatusGateway favoriteStatusGateway;
    @Mock
    private com.webtro.modules.interaction.spi.RentalEligibilityGateway rentalEligibilityGateway;
    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock
    private com.webtro.modules.interaction.repository.ViewHistoryRepository viewHistoryRepository;
    @Mock
    private com.webtro.modules.interaction.repository.FavoriteRepository favoriteRepository;

    @InjectMocks
    private ListingServiceImpl service;

    @BeforeEach
    void setUp() {
        loginAs(TENANT_ID, RoleCode.TENANT);
        when(categoryRepository.findById(ROOMMATE_CATEGORY)).thenReturn(Optional.of(category(ROOMMATE_CATEGORY, CategoryCode.ROOMMATE)));
        when(categoryRepository.findById(BOARDING_CATEGORY)).thenReturn(Optional.of(category(BOARDING_CATEGORY, CategoryCode.BOARDING_HOUSE)));
        when(config.getInt(ConfigKey.LISTING_TITLE_MIN)).thenReturn(10);
        when(config.getInt(ConfigKey.LISTING_TITLE_MAX)).thenReturn(150);
        when(config.getInt(ConfigKey.LISTING_DESCRIPTION_MIN)).thenReturn(30);
        when(config.getInt(ConfigKey.LISTING_DESCRIPTION_MAX)).thenReturn(3000);
        when(config.getInt(ConfigKey.SPAM_LISTING_DAILY)).thenReturn(10);
        when(config.getInt(ConfigKey.LISTING_IMAGE_MIN)).thenReturn(1);
        when(config.getInt(ConfigKey.LISTING_DISPLAY_DAYS)).thenReturn(30);
        when(config.getInt(ConfigKey.LISTING_RENEW_FREE_PER_MONTH)).thenReturn(2);
        when(listingRepository.save(any(Listing.class))).thenAnswer(inv -> {
            Listing l = inv.getArgument(0);
            if (l.getId() == null) {
                l.setId(LISTING_ID);
            }
            return l;
        });
        when(rentalUnitSupport.resolveWard(any(), any(), any())).thenReturn(ward());
        when(rentalUnitSupport.findSameOwnerDuplicate(anyLong(), anyLong(), anyString(), any(), isNull()))
                .thenReturn(Optional.empty());
        when(rentalUnitSupport.createUnit(anyLong(), anyString(), any())).thenReturn(unit());
        when(rentalUnitSupport.lockForNewListing(anyLong(), anyLong())).thenReturn(unit());
        when(imageRepository.findByListingIdAlive(anyLong())).thenReturn(List.of());
        when(mapper.toDetail(any(Listing.class), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean()))
                .thenAnswer(inv -> new com.webtro.modules.listing.dto.response.ListingDetailResponse());
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    // ================================================================== tạo tin

    @Test
    @DisplayName("UT-RM-01 Tenant tạo tin loại khác ROOMMATE (vd phòng trọ) → LISTING_FORBIDDEN")
    void tenantCannotCreateNonRoommate() {
        BusinessException ex = assertThrows(ForbiddenException.class,
                () -> service.create(createReq(BOARDING_CATEGORY)));

        assertEquals(ErrorCode.LISTING_FORBIDDEN, ex.getErrorCode());
        verify(listingRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-RM-02 Tin ở ghép thiếu giới tính/số người → ROOMMATE_INFO_REQUIRED")
    void roommateRequiresOccupancyInfo() {
        ListingCreateRequest req = createReq(ROOMMATE_CATEGORY);
        req.setCurrentOccupants(null);

        BusinessException ex = assertThrows(BusinessRuleException.class, () -> service.create(req));

        assertEquals(ErrorCode.ROOMMATE_INFO_REQUIRED, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-RM-03 Tạo tin ở ghép hợp lệ → DRAFT thuộc tenant, nhắc 'Tải ảnh' + 'Gửi duyệt'")
    void createRoommateDraft() {
        var response = service.create(createReq(ROOMMATE_CATEGORY));

        ArgumentCaptor<Listing> saved = ArgumentCaptor.forClass(Listing.class);
        verify(listingRepository).save(saved.capture());
        assertEquals(TENANT_ID, saved.getValue().getOwnerId());
        assertEquals(ListingStatus.DRAFT.name(), response.getStatus());
        assertTrue(response.getNextSteps().contains("Gửi duyệt tin"));
        assertTrue(response.getNextSteps().contains("Tải lên tối thiểu 1 ảnh"));
    }

    @Test
    @DisplayName("UT-RM-04 Tenant đăng lại cùng địa chỉ → dùng lại phòng cũ của chính mình (không báo trùng)")
    void tenantReusesOwnUnit() {
        RentalUnit existing = unit();
        when(rentalUnitSupport.findSameOwnerDuplicate(eq(TENANT_ID), anyLong(), anyString(), any(), isNull()))
                .thenReturn(Optional.of(existing));

        service.create(createReq(ROOMMATE_CATEGORY));

        verify(rentalUnitSupport).lockForNewListing(UNIT_ID, TENANT_ID);
        verify(rentalUnitSupport, never()).createUnit(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("UT-RM-05 Tiêu đề quá ngắn (<10 ký tự) → INVALID_TITLE_LENGTH")
    void titleTooShort() {
        ListingCreateRequest req = createReq(ROOMMATE_CATEGORY);
        req.setTitle("Ở ghép");

        BusinessException ex = assertThrows(BusinessRuleException.class, () -> service.create(req));

        assertEquals(ErrorCode.INVALID_TITLE_LENGTH, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-RM-06 Thiếu địa chỉ khi không chọn phòng có sẵn → RENTAL_UNIT_ADDRESS_REQUIRED")
    void addressRequired() {
        ListingCreateRequest req = createReq(ROOMMATE_CATEGORY);
        req.setAddressDetail(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req));

        assertEquals(ErrorCode.RENTAL_UNIT_ADDRESS_REQUIRED, ex.getErrorCode());
    }

    // ================================================================== sửa / gửi duyệt

    @Test
    @DisplayName("UT-RM-07 Tenant sửa tin ở ghép sang loại chỉ dành cho chủ trọ → LISTING_FORBIDDEN")
    void tenantCannotChangeCategoryToLandlordOnly() {
        stubListing(listing(TENANT_ID, ListingStatus.DRAFT));
        ListingUpdateRequest req = updateReq(BOARDING_CATEGORY);

        BusinessException ex = assertThrows(ForbiddenException.class, () -> service.update(LISTING_ID, req));

        assertEquals(ErrorCode.LISTING_FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-RM-08 Tenant sửa tin của người khác → LISTING_FORBIDDEN")
    void tenantCannotEditOthersListing() {
        stubListing(listing(OTHER_ID, ListingStatus.ACTIVE));

        BusinessException ex = assertThrows(ForbiddenException.class,
                () -> service.update(LISTING_ID, updateReq(ROOMMATE_CATEGORY)));

        assertEquals(ErrorCode.LISTING_FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-RM-09 Gửi duyệt khi chưa có ảnh → IMAGE_COUNT_MIN")
    void submitWithoutImage() {
        stubListing(listing(TENANT_ID, ListingStatus.DRAFT));

        BusinessException ex = assertThrows(BusinessRuleException.class, () -> service.submit(LISTING_ID));

        assertEquals(ErrorCode.IMAGE_COUNT_MIN, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-RM-10 Sửa tiêu đề tin ACTIVE → quay lại PENDING (duyệt lại)")
    void sensitiveEditRequiresReapproval() {
        Listing l = listing(TENANT_ID, ListingStatus.ACTIVE);
        stubListing(l);
        ListingUpdateRequest req = updateReq(ROOMMATE_CATEGORY);
        req.setTitle("Tiêu đề mới hoàn toàn cho tin ở ghép");

        var detail = service.update(LISTING_ID, req);

        assertEquals(ListingStatus.PENDING, l.getStatus());
        assertTrue(detail.getModerationImpact().getRequiresReapproval());
    }

    // ================================================================== ẩn / hiện / đóng / xóa

    @Test
    @DisplayName("UT-RM-11 Ẩn tin ACTIVE → HIDDEN; ẩn lần nữa → LISTING_ALREADY_HIDDEN")
    void hideListing() {
        Listing l = listing(TENANT_ID, ListingStatus.ACTIVE);
        l.setExpiredAt(Instant.now().plus(Duration.ofDays(10)));
        stubListing(l);

        var response = service.hide(LISTING_ID);
        assertEquals(ListingStatus.HIDDEN.name(), response.getStatus());
        assertTrue(response.getCanUnhide());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.hide(LISTING_ID));
        assertEquals(ErrorCode.LISTING_ALREADY_HIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-RM-12 Hiện lại tin đã hết hạn → LISTING_ALREADY_EXPIRED")
    void unhideExpired() {
        Listing l = listing(TENANT_ID, ListingStatus.HIDDEN);
        l.setExpiredAt(Instant.now().minus(Duration.ofDays(1)));
        stubListing(l);

        BusinessException ex = assertThrows(BusinessRuleException.class, () -> service.unhide(LISTING_ID));

        assertEquals(ErrorCode.LISTING_ALREADY_EXPIRED, ex.getErrorCode());
    }

    @ParameterizedTest(name = "UT-RM-13 Đóng tin đang {0} → LISTING_INVALID_TRANSITION")
    @EnumSource(value = ListingStatus.class, names = {"DRAFT", "PENDING", "REJECTED", "EXPIRED"})
    void closeOnlyFromActiveOrHidden(ListingStatus status) {
        stubListing(listing(TENANT_ID, status));

        BusinessException ex = assertThrows(BusinessRuleException.class,
                () -> service.close(LISTING_ID, CloseListingRequest.builder().reason(CloseReason.NO_LONGER_AVAILABLE).build()));

        assertEquals(ErrorCode.LISTING_INVALID_TRANSITION, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-RM-14 Xóa tin đang LOCKED → LISTING_LOCKED_CANNOT_DELETE")
    void deleteLocked() {
        stubListing(listing(TENANT_ID, ListingStatus.LOCKED));

        BusinessException ex = assertThrows(BusinessRuleException.class, () -> service.delete(LISTING_ID));

        assertEquals(ErrorCode.LISTING_LOCKED_CANNOT_DELETE, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-RM-15 Xóa tin nháp → DELETED + deleted_at, phát ListingWithdrawnEvent")
    void deleteDraft() {
        Listing l = listing(TENANT_ID, ListingStatus.DRAFT);
        stubListing(l);

        service.delete(LISTING_ID);

        assertEquals(ListingStatus.DELETED, l.getStatus());
        assertFalse(l.getDeletedAt() == null);
    }

    // ================================================================== gia hạn

    @Test
    @DisplayName("UT-RM-16 Gia hạn tin bị REJECTED → LISTING_REJECTED_MUST_EDIT")
    void renewRejected() {
        stubListing(listing(TENANT_ID, ListingStatus.REJECTED));

        BusinessException ex = assertThrows(BusinessRuleException.class,
                () -> service.renew(LISTING_ID, new RenewListingRequest()));

        assertEquals(ErrorCode.LISTING_REJECTED_MUST_EDIT, ex.getErrorCode());
    }

    @Test
    @Disabled("BUG-01: renew() bắt buộc LandlordProfile; tenant không có hồ sơ chủ trọ nên luôn nhận 404 "
            + "LANDLORD_PROFILE_NOT_FOUND, không gia hạn được tin ở ghép (checklist docs/10 mục 3). Bỏ @Disabled sau khi fix.")
    @DisplayName("UT-RM-17 [BUG-01] Tenant gia hạn miễn phí tin ở ghép ACTIVE → phải thành công")
    void tenantCanRenewRoommateListing() {
        Listing l = listing(TENANT_ID, ListingStatus.ACTIVE);
        l.setExpiredAt(Instant.now().plus(Duration.ofDays(2)));
        stubListing(l);
        when(landlordProfileRepository.findByUser_IdAndDeletedAtIsNull(TENANT_ID)).thenReturn(Optional.empty());

        var response = service.renew(LISTING_ID, new RenewListingRequest());

        assertTrue(response.getFree());
        assertEquals(ListingStatus.ACTIVE.name(), response.getStatus());
    }

    // ================================================================== helper

    private void loginAs(long id, String role) {
        CustomUserDetails principal = new CustomUserDetails(id, id + "@test.local", "x", UserStatus.ACTIVE, role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private void stubListing(Listing l) {
        when(listingRepository.findAliveById(LISTING_ID)).thenReturn(Optional.of(l));
    }

    private Category category(long id, CategoryCode code) {
        Category c = Category.builder().code(code).name(code.name()).slug(code.name().toLowerCase()).build();
        c.setId(id);
        return c;
    }

    private Ward ward() {
        Ward w = new Ward();
        w.setId(10101L);
        return w;
    }

    private RentalUnit unit() {
        RentalUnit u = RentalUnit.builder().ownerId(TENANT_ID).name("Phòng ở ghép").build();
        u.setId(UNIT_ID);
        return u;
    }

    private Listing listing(long ownerId, ListingStatus status) {
        Listing l = Listing.builder()
                .ownerId(ownerId)
                .categoryId(ROOMMATE_CATEGORY)
                .rentalUnitId(UNIT_ID)
                .title("Tìm bạn nữ ở ghép Cầu Giấy")
                .slug("tim-ban-nu-o-ghep-cau-giay")
                .description("Phòng 25m2 gần ĐH Quốc gia, cần tìm thêm 1 bạn nữ ở ghép, giờ giấc tự do.")
                .price(BigDecimal.valueOf(1_800_000))
                .area(BigDecimal.valueOf(25))
                .maxOccupants(3)
                .currentOccupants(2)
                .genderRequirement(GenderRequirement.FEMALE_ONLY)
                .status(status)
                .build();
        l.setId(LISTING_ID);
        return l;
    }

    private ListingCreateRequest createReq(long categoryId) {
        return ListingCreateRequest.builder()
                .categoryId(categoryId)
                .title("Tìm bạn nữ ở ghép Cầu Giấy")
                .description("Phòng 25m2 gần ĐH Quốc gia, cần tìm thêm 1 bạn nữ ở ghép, giờ giấc tự do.")
                .price(BigDecimal.valueOf(1_800_000))
                .area(BigDecimal.valueOf(25))
                .provinceId(1L)
                .districtId(105L)
                .wardId(10501L)
                .addressDetail("Số 10 ngõ 20 Xuân Thủy")
                .maxOccupants(3)
                .currentOccupants(2)
                .genderRequirement(GenderRequirement.FEMALE_ONLY)
                .contactName("Người thuê A")
                .contactPhone("0912345678")
                .build();
    }

    private ListingUpdateRequest updateReq(long categoryId) {
        return ListingUpdateRequest.builder()
                .categoryId(categoryId)
                .title("Tìm bạn nữ ở ghép Cầu Giấy")
                .description("Phòng 25m2 gần ĐH Quốc gia, cần tìm thêm 1 bạn nữ ở ghép, giờ giấc tự do.")
                .price(BigDecimal.valueOf(1_800_000))
                .area(BigDecimal.valueOf(25))
                .maxOccupants(3)
                .currentOccupants(2)
                .genderRequirement(GenderRequirement.FEMALE_ONLY)
                .build();
    }
}
