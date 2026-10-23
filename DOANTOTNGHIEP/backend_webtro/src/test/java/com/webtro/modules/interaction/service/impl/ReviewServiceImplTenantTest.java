package com.webtro.modules.interaction.service.impl;

import com.webtro.common.enums.BannedKeywordSeverity;
import com.webtro.common.enums.NotificationType;
import com.webtro.common.enums.ReviewStatus;
import com.webtro.common.enums.UserStatus;
import com.webtro.constant.ErrorCode;
import com.webtro.constant.RoleCode;
import com.webtro.exception.BusinessException;
import com.webtro.exception.BusinessRuleException;
import com.webtro.exception.ConflictException;
import com.webtro.exception.ForbiddenException;
import com.webtro.exception.RateLimitException;
import com.webtro.modules.admin.service.AuditLogService;
import com.webtro.modules.interaction.dto.request.CreateReviewRequest;
import com.webtro.modules.interaction.entity.Review;
import com.webtro.modules.interaction.mapper.ReviewMapper;
import com.webtro.modules.interaction.repository.ReviewRepository;
import com.webtro.modules.interaction.service.ReviewRatingRecalculator;
import com.webtro.modules.interaction.spi.BannedKeywordGateway;
import com.webtro.modules.interaction.spi.ListingGateway;
import com.webtro.modules.interaction.spi.RentalEligibilityGateway;
import com.webtro.modules.interaction.spi.UserGateway;
import com.webtro.modules.notification.service.NotificationService;
import com.webtro.security.CustomUserDetails;
import com.webtro.security.RateLimitService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UT bổ sung cho đánh giá phía người thuê (UT-REV-xx) — các case chưa có trong ReviewServiceImplTest:
 * ràng buộc nội dung theo số sao, từ cấm, quyền sửa/xóa, tạo lại sau khi xóa, rate limit, thông báo.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewServiceImplTenantTest {

    private static final long REVIEW_ID = 10L;
    private static final long LISTING_ID = 20L;
    private static final long UNIT_ID = 900L;
    private static final long TENANT_ID = 30L;
    private static final long OTHER_TENANT_ID = 31L;
    private static final long LANDLORD_ID = 40L;

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private ListingGateway listingGateway;
    @Mock
    private RentalEligibilityGateway rentalEligibilityGateway;
    @Mock
    private UserGateway userGateway;
    @Mock
    private BannedKeywordGateway bannedKeywordGateway;
    @Mock
    private NotificationService notificationService;
    @Mock
    private RateLimitService rateLimitService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private ReviewRatingRecalculator ratingRecalculator;

    private ReviewServiceImpl service;
    private final ReviewRatingRecalculator.Scope scope = new ReviewRatingRecalculator.Scope(UNIT_ID, LANDLORD_ID, true);

    @BeforeEach
    void setUp() {
        service = new ReviewServiceImpl(reviewRepository, listingGateway, rentalEligibilityGateway, userGateway,
                bannedKeywordGateway, new ReviewMapper(), notificationService, rateLimitService, auditLogService,
                ratingRecalculator);
        when(listingGateway.rentalUnitIdOf(LISTING_ID)).thenReturn(UNIT_ID);
        when(listingGateway.getUnitBrief(UNIT_ID)).thenReturn(
                new ListingGateway.RentalUnitBrief(UNIT_ID, LANDLORD_ID, "Phòng 101", "101", BigDecimal.ZERO, 0, false));
        when(bannedKeywordGateway.scan(anyString(), any()))
                .thenReturn(new BannedKeywordGateway.ScanResult(Optional.empty(), List.of()));
        when(ratingRecalculator.lock(UNIT_ID)).thenReturn(scope);
        when(ratingRecalculator.recalc(scope)).thenReturn(
                new ReviewRatingRecalculator.Result(new BigDecimal("4.00"), 1, new BigDecimal("4.00"), 1));
        when(rentalEligibilityGateway.findRentedListingInUnit(UNIT_ID, TENANT_ID)).thenReturn(Optional.of(LISTING_ID));
        when(reviewRepository.saveAndFlush(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userGateway.findBrief(any())).thenReturn(Optional.empty());
        loginAs(TENANT_ID, RoleCode.TENANT);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest(name = "UT-REV-01 Chấm {0} sao không kèm nội dung → REVIEW_CONTENT_REQUIRED")
    @ValueSource(ints = {1, 2})
    void lowRatingRequiresContent(int rating) {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createReview(LISTING_ID, req(rating, "   "), TENANT_ID));

        assertEquals(ErrorCode.REVIEW_CONTENT_REQUIRED, ex.getErrorCode());
        verify(reviewRepository, never()).saveAndFlush(any());
    }

    @ParameterizedTest(name = "UT-REV-02 Chấm {0} sao không kèm nội dung → hợp lệ, nội dung null")
    @ValueSource(ints = {3, 4, 5})
    void highRatingContentOptional(int rating) {
        var response = service.createReview(LISTING_ID, req(rating, null), TENANT_ID);

        assertEquals(rating, response.getRating());
        assertNull(response.getContent());
    }

    @Test
    @DisplayName("UT-REV-03 Nội dung chứa từ cấm (kể cả MILD) → BANNED_KEYWORD_DETECTED")
    void bannedKeywordRejected() {
        when(bannedKeywordGateway.scan(anyString(), any())).thenReturn(
                new BannedKeywordGateway.ScanResult(Optional.of(BannedKeywordSeverity.MILD), List.of("xxx")));

        BusinessException ex = assertThrows(BusinessRuleException.class,
                () -> service.createReview(LISTING_ID, req(4, "Phòng ổn nhưng xxx"), TENANT_ID));

        assertEquals(ErrorCode.BANNED_KEYWORD_DETECTED, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-REV-04 Tạo đánh giá hợp lệ → lọc HTML, VISIBLE, tính lại điểm, báo chủ trọ NEW_REVIEW")
    void createNotifiesLandlord() {
        var response = service.createReview(LISTING_ID, req(4, "<b>Phòng sạch sẽ</b>, chủ nhà thân thiện"), TENANT_ID);

        assertEquals("Phòng sạch sẽ, chủ nhà thân thiện", response.getContent());
        assertEquals(new BigDecimal("4.00"), response.getListingAverageRating());
        verify(ratingRecalculator).recalc(scope);
        verify(notificationService).notifyUser(eq(LANDLORD_ID), eq(NotificationType.NEW_REVIEW), anyString(), anyString());
    }

    @Test
    @DisplayName("UT-REV-05 Hai request tạo đồng thời đụng unique → REVIEW_ALREADY_EXISTS (không 500)")
    void concurrentCreateMapsToConflict() {
        when(reviewRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk"));

        BusinessException ex = assertThrows(ConflictException.class,
                () -> service.createReview(LISTING_ID, req(5, null), TENANT_ID));

        assertEquals(ErrorCode.REVIEW_ALREADY_EXISTS, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-REV-06 Vượt 10 đánh giá/ngày → RateLimitException, chưa đụng DB")
    void rateLimited() {
        doThrow(new RateLimitException(ErrorCode.RATE_LIMIT_EXCEEDED, 3600L))
                .when(rateLimitService).checkLimit(eq("review"), eq(String.valueOf(TENANT_ID)), anyInt(), any(Duration.class));

        assertThrows(RateLimitException.class, () -> service.createReview(LISTING_ID, req(5, null), TENANT_ID));
        verify(ratingRecalculator, never()).lock(any());
    }

    @Test
    @DisplayName("UT-REV-07 Tạo lại sau khi đã tự xóa (bản cũ deleted_at != null) → cho phép")
    void recreateAfterSelfDelete() {
        when(reviewRepository.existsByUserIdAndRentalUnitIdAndDeletedAtIsNull(TENANT_ID, UNIT_ID)).thenReturn(false);

        var response = service.createReview(LISTING_ID, req(3, "Đánh giá lại sau khi xóa"), TENANT_ID);

        assertEquals(3, response.getRating());
    }

    @Test
    @DisplayName("UT-REV-08 Tenant sửa đánh giá của tenant khác → REVIEW_FORBIDDEN")
    void updateOthersReview() {
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review(OTHER_TENANT_ID, ReviewStatus.VISIBLE)));

        BusinessException ex = assertThrows(ForbiddenException.class,
                () -> service.updateReview(REVIEW_ID, req(1, "Sửa trộm đánh giá người khác"), TENANT_ID));

        assertEquals(ErrorCode.REVIEW_FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-REV-09 Sửa đánh giá đã bị kiểm duyệt ẩn → REVIEW_INVALID_TRANSITION")
    void updateHiddenReview() {
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review(TENANT_ID, ReviewStatus.HIDDEN)));

        BusinessException ex = assertThrows(BusinessRuleException.class,
                () -> service.updateReview(REVIEW_ID, req(5, "Sửa sau khi bị ẩn"), TENANT_ID));

        assertEquals(ErrorCode.REVIEW_INVALID_TRANSITION, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-REV-10 Sửa đánh giá của mình → cập nhật số sao/nội dung, editedAt, tính lại điểm")
    void updateOwnReview() {
        Review r = review(TENANT_ID, ReviewStatus.VISIBLE);
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(r));

        var response = service.updateReview(REVIEW_ID, req(2, "Sau 1 tháng thấy hơi ồn"), TENANT_ID);

        assertEquals(2, r.getRating());
        assertNotNull(r.getEditedAt());
        assertEquals(2, response.getRating());
        verify(ratingRecalculator).recalc(scope);
    }

    @Test
    @DisplayName("UT-REV-11 Tenant xóa đánh giá của người khác → REVIEW_FORBIDDEN")
    void deleteOthersReview() {
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review(OTHER_TENANT_ID, ReviewStatus.VISIBLE)));

        assertThrows(ForbiddenException.class, () -> service.deleteReview(REVIEW_ID, TENANT_ID, null));
        verify(reviewRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("UT-REV-12 Tác giả xóa đánh giá → DELETED, activeMarker = null (cho phép tạo lại), tính lại điểm")
    void deleteOwnReview() {
        Review r = review(TENANT_ID, ReviewStatus.VISIBLE);
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(r));

        service.deleteReview(REVIEW_ID, TENANT_ID, null);

        assertEquals(ReviewStatus.DELETED, r.getStatus());
        assertNull(r.getActiveMarker());
        assertNotNull(r.getDeletedAt());
        verify(ratingRecalculator).recalc(scope);
    }

    @Test
    @DisplayName("UT-REV-13 Kiểm tra điều kiện: chưa từng thuê → eligible=false, reason=NOT_RENTER")
    void eligibilityNotRenter() {
        when(rentalEligibilityGateway.findRentedListingInUnit(UNIT_ID, TENANT_ID)).thenReturn(Optional.empty());

        var e = service.checkEligibility(LISTING_ID, TENANT_ID);

        assertEquals(false, e.getEligible());
        assertEquals("NOT_RENTER", e.getReason());
    }

    private void loginAs(long id, String role) {
        CustomUserDetails principal = new CustomUserDetails(id, id + "@test.local", "x", UserStatus.ACTIVE, role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private CreateReviewRequest req(int rating, String content) {
        return CreateReviewRequest.builder().rating(rating).content(content).build();
    }

    private Review review(long userId, ReviewStatus status) {
        return Review.builder()
                .id(REVIEW_ID)
                .rentalUnitId(UNIT_ID)
                .listingId(LISTING_ID)
                .userId(userId)
                .rating(5)
                .content("Đánh giá ban đầu hợp lệ")
                .status(status)
                .createdAt(Instant.now().minus(Duration.ofDays(3)))
                .build();
    }
}
