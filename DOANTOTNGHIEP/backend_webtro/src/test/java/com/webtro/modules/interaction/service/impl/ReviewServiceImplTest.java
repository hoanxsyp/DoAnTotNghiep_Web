package com.webtro.modules.interaction.service.impl;

import com.webtro.common.enums.ListingStatus;
import com.webtro.common.enums.ReviewStatus;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessRuleException;
import com.webtro.exception.ConflictException;
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
import com.webtro.security.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Đánh giá theo Phòng (docs/DANH_GIA_THEO_PHONG_DOI_CHIEU.md mục 3, 10): quyền đánh giá xét theo
 * phòng, đánh giá lưu tin đã thuê qua, và mọi thay đổi điểm đi qua ReviewRatingRecalculator
 * (khoá trước, tính lại sau).
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    private static final long REVIEW_ID = 10L;
    private static final long LISTING_ID = 20L;
    private static final long OLD_LISTING_ID = 21L;
    private static final long UNIT_ID = 900L;
    private static final long USER_ID = 30L;
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

    private final ReviewRatingRecalculator.Scope scope =
            new ReviewRatingRecalculator.Scope(UNIT_ID, LANDLORD_ID, true);
    private final ReviewRatingRecalculator.Result result = new ReviewRatingRecalculator.Result(
            new BigDecimal("4.50"), 2, new BigDecimal("4.20"), 5);

    @BeforeEach
    void setUp() {
        service = new ReviewServiceImpl(
                reviewRepository,
                listingGateway,
                rentalEligibilityGateway,
                userGateway,
                bannedKeywordGateway,
                new ReviewMapper(),
                notificationService,
                rateLimitService,
                auditLogService,
                ratingRecalculator);
    }

    // ------------------------------------------------------------------ tạo đánh giá

    @Test
    void createReviewAllowsRenterOfAnOlderListingOfTheSameUnit() {
        stubUnitOfListing();
        when(ratingRecalculator.lock(UNIT_ID)).thenReturn(scope);
        when(reviewRepository.existsByUserIdAndRentalUnitIdAndDeletedAtIsNull(USER_ID, UNIT_ID)).thenReturn(false);
        // Người này đã thuê qua tin CŨ của phòng (OLD_LISTING_ID), giờ đánh giá từ trang tin MỚI.
        when(rentalEligibilityGateway.findRentedListingInUnit(UNIT_ID, USER_ID))
                .thenReturn(Optional.of(OLD_LISTING_ID));
        when(reviewRepository.saveAndFlush(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ratingRecalculator.recalc(scope)).thenReturn(result);

        var response = service.createReview(LISTING_ID, request(5, "Phòng rất tốt"), USER_ID);

        ArgumentCaptor<Review> saved = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).saveAndFlush(saved.capture());
        assertEquals(UNIT_ID, saved.getValue().getRentalUnitId());
        assertEquals(OLD_LISTING_ID, saved.getValue().getListingId());
        assertEquals(new BigDecimal("4.50"), response.getListingAverageRating());
        assertEquals(2, response.getListingReviewCount());
        assertEquals(new BigDecimal("4.20"), response.getLandlordAverageRating());
        assertEquals("Phòng 101", response.getRentalUnitName());

        // Khoá phải xảy ra TRƯỚC khi ghi đánh giá, tính lại SAU khi ghi.
        InOrder order = inOrder(ratingRecalculator, reviewRepository);
        order.verify(ratingRecalculator).lock(UNIT_ID);
        order.verify(reviewRepository).saveAndFlush(any(Review.class));
        order.verify(ratingRecalculator).recalc(scope);
    }

    @Test
    void createReviewRejectsUserWhoNeverRentedTheUnit() {
        stubUnitOfListing();
        when(ratingRecalculator.lock(UNIT_ID)).thenReturn(scope);
        when(rentalEligibilityGateway.findRentedListingInUnit(UNIT_ID, USER_ID)).thenReturn(Optional.empty());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.createReview(LISTING_ID, request(4, null), USER_ID));

        assertEquals(ErrorCode.REVIEW_RENTAL_REQUIRED, ex.getErrorCode());
        verify(reviewRepository, never()).saveAndFlush(any());
        verify(ratingRecalculator, never()).recalc(any());
    }

    @Test
    void createReviewRejectsSecondReviewForTheSameUnit() {
        stubUnitOfListing();
        when(ratingRecalculator.lock(UNIT_ID)).thenReturn(scope);
        when(reviewRepository.existsByUserIdAndRentalUnitIdAndDeletedAtIsNull(USER_ID, UNIT_ID)).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.createReview(LISTING_ID, request(4, null), USER_ID));
        verify(reviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void createReviewRejectsUnitOwner() {
        when(listingGateway.rentalUnitIdOf(LISTING_ID)).thenReturn(UNIT_ID);
        when(listingGateway.getUnitBrief(UNIT_ID)).thenReturn(unitBrief(USER_ID));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.createReview(LISTING_ID, request(5, null), USER_ID));

        assertEquals(ErrorCode.REVIEW_SELF_FORBIDDEN, ex.getErrorCode());
        verify(ratingRecalculator, never()).lock(any());
    }

    // ------------------------------------------------------------------ quyền đánh giá

    @Test
    void eligibilityIsCheckedPerUnit() {
        stubUnitOfListing();
        when(reviewRepository.findByUserIdAndRentalUnitIdAndDeletedAtIsNull(USER_ID, UNIT_ID))
                .thenReturn(Optional.empty());
        when(rentalEligibilityGateway.findRentedListingInUnit(UNIT_ID, USER_ID))
                .thenReturn(Optional.of(OLD_LISTING_ID));

        var eligibility = service.checkEligibility(LISTING_ID, USER_ID);

        assertTrue(eligibility.getEligible());
        assertEquals(UNIT_ID, eligibility.getRentalUnitId());
        assertTrue(eligibility.getRentalVerified());
    }

    // ------------------------------------------------------------------ sửa / xoá / ẩn

    @Test
    void updateReviewLocksThenSavesThenRecalculates() {
        Review review = review(REVIEW_ID, ReviewStatus.VISIBLE);
        stubAliveReview(review);
        when(ratingRecalculator.lock(UNIT_ID)).thenReturn(scope);
        when(ratingRecalculator.recalc(scope)).thenReturn(result);
        when(listingGateway.getUnitBrief(UNIT_ID)).thenReturn(unitBrief(LANDLORD_ID));
        when(userGateway.findBrief(USER_ID)).thenReturn(Optional.of(userBrief()));

        var response = service.updateReview(REVIEW_ID, request(3, "Nội dung sau khi chỉnh sửa"), USER_ID);

        assertEquals(3, review.getRating());
        assertEquals("Nội dung sau khi chỉnh sửa", review.getContent());
        assertNotNull(review.getEditedAt());
        assertTrue(response.getEditable());
        assertEquals(new BigDecimal("4.50"), response.getListingAverageRating());
        InOrder order = inOrder(ratingRecalculator, reviewRepository);
        order.verify(ratingRecalculator).lock(UNIT_ID);
        order.verify(reviewRepository).saveAndFlush(review);
        order.verify(ratingRecalculator).recalc(scope);
    }

    @Test
    void deleteReviewByAuthorRecalculatesWithoutTouchingTheListing() {
        Review review = review(REVIEW_ID, ReviewStatus.VISIBLE);
        stubAliveReview(review);
        when(ratingRecalculator.lock(UNIT_ID)).thenReturn(scope);

        service.deleteReview(REVIEW_ID, USER_ID, null);

        assertEquals(ReviewStatus.DELETED, review.getStatus());
        assertNull(review.getActiveMarker());
        assertNotNull(review.getDeletedAt());
        verify(reviewRepository).saveAndFlush(review);
        verify(ratingRecalculator).recalc(scope);
        // Không cần đọc tin: đánh giá trên tin đã xoá mềm vẫn xoá được (lỗ hổng 8).
        verify(listingGateway, never()).getBrief(any());
    }

    @Test
    void hidingAnAlreadyHiddenReviewDoesNotRecalculate() {
        Review review = review(REVIEW_ID, ReviewStatus.HIDDEN);
        review.setHiddenReason("Đã ẩn trước đó vì vi phạm");
        stubAliveReview(review);
        when(ratingRecalculator.lock(UNIT_ID)).thenReturn(scope);

        service.hideByModeration(REVIEW_ID, 99L, "Ẩn lại lần nữa vì vi phạm");

        verify(ratingRecalculator, never()).recalc(any());
    }

    // ------------------------------------------------------------------ đọc

    @Test
    void listListingReviewsReturnsReviewsAndScoreOfTheUnit() {
        PageRequest pageable = PageRequest.of(0, 20);
        stubUnitOfListing();
        Review r = review(REVIEW_ID, ReviewStatus.VISIBLE);
        when(reviewRepository.findByUnit(UNIT_ID, ReviewStatus.VISIBLE, null, pageable))
                .thenReturn(new PageImpl<>(List.of(r), pageable, 1));
        when(reviewRepository.ratingDistributionByUnit(UNIT_ID, ReviewStatus.VISIBLE))
                .thenReturn(List.<Object[]>of(new Object[]{5, 1L}));
        when(listingGateway.getUnitBriefs(List.of(UNIT_ID))).thenReturn(Map.of(UNIT_ID, unitBrief(LANDLORD_ID)));
        when(listingGateway.getBriefsIncludingDeleted(List.of(OLD_LISTING_ID)))
                .thenReturn(Map.of(OLD_LISTING_ID, listingBrief(OLD_LISTING_ID, "Tin cũ của phòng")));

        var page = service.listListingReviews(LISTING_ID, null, pageable);

        assertEquals(UNIT_ID, page.getSummary().getRentalUnitId());
        assertEquals(new BigDecimal("4.50"), page.getSummary().getAverageRating());
        assertEquals(1L, page.getSummary().getDistribution().get("5"));
        assertEquals(0L, page.getSummary().getDistribution().get("1"));
        assertEquals("Tin cũ của phòng", page.getPage().getItems().get(0).getRentedViaListingTitle());
    }

    @Test
    void listMyReviewsReturnsVisibleAndHiddenReviewsWithManagementState() {
        Review visible = review(REVIEW_ID, ReviewStatus.VISIBLE);
        Review hidden = review(REVIEW_ID + 1, ReviewStatus.HIDDEN);
        hidden.setHiddenReason("Nội dung đang được kiểm duyệt");
        PageRequest pageable = PageRequest.of(0, 20);

        when(reviewRepository.findByUserIdAndDeletedAtIsNull(USER_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(visible, hidden), pageable, 2));
        when(listingGateway.getBriefsIncludingDeleted(List.of(OLD_LISTING_ID)))
                .thenReturn(Map.of(OLD_LISTING_ID, listingBrief(OLD_LISTING_ID, "Phòng thử nghiệm")));
        when(listingGateway.getUnitBriefs(List.of(UNIT_ID))).thenReturn(Map.of(UNIT_ID, unitBrief(LANDLORD_ID)));
        when(userGateway.findBrief(USER_ID)).thenReturn(Optional.of(userBrief()));

        var result = service.listMyReviews(USER_ID, pageable);

        assertEquals(2, result.getItems().size());
        assertTrue(result.getItems().get(0).getEditable());
        assertFalse(result.getItems().get(1).getEditable());
        assertEquals("Nội dung đang được kiểm duyệt", result.getItems().get(1).getModerationReason());
        assertEquals("Phòng thử nghiệm", result.getItems().get(0).getListingTitle());
        assertEquals("Phòng 101", result.getItems().get(0).getRentalUnitName());
    }

    // ------------------------------------------------------------------ helper

    private void stubUnitOfListing() {
        when(listingGateway.rentalUnitIdOf(LISTING_ID)).thenReturn(UNIT_ID);
        when(listingGateway.getUnitBrief(UNIT_ID)).thenReturn(unitBrief(LANDLORD_ID));
    }

    private void stubAliveReview(Review review) {
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
    }

    private CreateReviewRequest request(int rating, String content) {
        if (content != null) {
            when(bannedKeywordGateway.scan(anyString(), any()))
                    .thenReturn(new BannedKeywordGateway.ScanResult(Optional.empty(), List.of()));
        }
        return CreateReviewRequest.builder().rating(rating).content(content).build();
    }

    private Review review(long id, ReviewStatus status) {
        return Review.builder()
                .id(id)
                .rentalUnitId(UNIT_ID)
                .listingId(OLD_LISTING_ID)
                .userId(USER_ID)
                .rating(5)
                .content("Đánh giá ban đầu hợp lệ")
                .status(status)
                .createdAt(Instant.now().minusSeconds(60L * 60 * 24 * 365))
                .build();
    }

    private ListingGateway.RentalUnitBrief unitBrief(long ownerId) {
        return new ListingGateway.RentalUnitBrief(UNIT_ID, ownerId, "Phòng 101", "101",
                new BigDecimal("4.50"), 2, true);
    }

    private ListingGateway.ListingBrief listingBrief(long id, String title) {
        return new ListingGateway.ListingBrief(
                id, LANDLORD_ID, title, "phong-thu-nghiem", null,
                BigDecimal.valueOf(3_000_000), BigDecimal.valueOf(25), "Hà Nội",
                ListingStatus.CLOSED, false, new BigDecimal("4.50"), 2, UNIT_ID, Instant.now());
    }

    private UserGateway.UserBrief userBrief() {
        return new UserGateway.UserBrief(USER_ID, "Người thuê", null, "0900000000", Instant.now());
    }
}
