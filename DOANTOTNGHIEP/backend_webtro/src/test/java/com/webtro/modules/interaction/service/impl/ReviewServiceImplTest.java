package com.webtro.modules.interaction.service.impl;

import com.webtro.common.enums.ListingStatus;
import com.webtro.common.enums.ReviewStatus;
import com.webtro.modules.admin.service.AuditLogService;
import com.webtro.modules.interaction.dto.request.CreateReviewRequest;
import com.webtro.modules.interaction.entity.Review;
import com.webtro.modules.interaction.mapper.ReviewMapper;
import com.webtro.modules.interaction.repository.ReviewRepository;
import com.webtro.modules.interaction.spi.BannedKeywordGateway;
import com.webtro.modules.interaction.spi.ListingGateway;
import com.webtro.modules.interaction.spi.RentalEligibilityGateway;
import com.webtro.modules.interaction.spi.UserGateway;
import com.webtro.modules.notification.service.NotificationService;
import com.webtro.security.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    private static final long REVIEW_ID = 10L;
    private static final long LISTING_ID = 20L;
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

    private ReviewServiceImpl service;

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
                auditLogService);
    }

    @Test
    void listMyReviewsReturnsVisibleAndHiddenReviewsWithManagementState() {
        Review visible = review(REVIEW_ID, ReviewStatus.VISIBLE);
        Review hidden = review(REVIEW_ID + 1, ReviewStatus.HIDDEN);
        hidden.setHiddenReason("Nội dung đang được kiểm duyệt");
        PageRequest pageable = PageRequest.of(0, 20);

        when(reviewRepository.findByUserIdAndDeletedAtIsNull(USER_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(visible, hidden), pageable, 2));
        when(listingGateway.getBriefs(List.of(LISTING_ID))).thenReturn(Map.of(LISTING_ID, listingBrief()));
        when(userGateway.findBrief(USER_ID)).thenReturn(Optional.of(userBrief()));

        var result = service.listMyReviews(USER_ID, pageable);

        assertEquals(2, result.getItems().size());
        assertTrue(result.getItems().get(0).getEditable());
        assertFalse(result.getItems().get(1).getEditable());
        assertEquals("Nội dung đang được kiểm duyệt", result.getItems().get(1).getModerationReason());
        assertEquals("Phòng thử nghiệm", result.getItems().get(0).getListingTitle());
    }

    @Test
    void updateReviewAllowsAuthorEvenWhenReviewIsOld() {
        Review review = review(REVIEW_ID, ReviewStatus.VISIBLE);
        CreateReviewRequest request = CreateReviewRequest.builder()
                .rating(3)
                .content("Nội dung sau khi chỉnh sửa")
                .build();
        stubAliveReview(review);
        stubAggregateDependencies();
        when(userGateway.findBrief(USER_ID)).thenReturn(Optional.of(userBrief()));
        when(bannedKeywordGateway.scan(any(), any()))
                .thenReturn(new BannedKeywordGateway.ScanResult(Optional.empty(), List.of()));
        when(reviewRepository.countByListingIdAndStatusAndDeletedAtIsNull(LISTING_ID, ReviewStatus.VISIBLE))
                .thenReturn(2L);

        var response = service.updateReview(REVIEW_ID, request, USER_ID);

        assertEquals(3, review.getRating());
        assertEquals("Nội dung sau khi chỉnh sửa", review.getContent());
        assertNotNull(review.getEditedAt());
        assertTrue(response.getEditable());
        verify(reviewRepository).save(review);
    }

    @Test
    void deleteReviewAllowsAuthorEvenWhenReviewIsOld() {
        Review review = review(REVIEW_ID, ReviewStatus.VISIBLE);
        stubAliveReview(review);
        stubAggregateDependencies();

        service.deleteReview(REVIEW_ID, USER_ID, null);

        assertEquals(ReviewStatus.DELETED, review.getStatus());
        assertNull(review.getActiveMarker());
        assertNotNull(review.getDeletedAt());
        verify(reviewRepository).save(review);
        verify(listingGateway).updateReviewAggregate(LISTING_ID, BigDecimal.valueOf(3.00).setScale(2), 1);
    }

    private void stubAliveReview(Review review) {
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
    }

    private void stubAggregateDependencies() {
        when(listingGateway.getBrief(LISTING_ID)).thenReturn(listingBrief());
        when(userGateway.getLandlordAggregate(LANDLORD_ID))
                .thenReturn(new UserGateway.LandlordAggregate(BigDecimal.valueOf(4), 2));
    }

    private Review review(long id, ReviewStatus status) {
        return Review.builder()
                .id(id)
                .listingId(LISTING_ID)
                .userId(USER_ID)
                .rating(5)
                .content("Đánh giá ban đầu hợp lệ")
                .status(status)
                .createdAt(Instant.now().minusSeconds(60L * 60 * 24 * 365))
                .build();
    }

    private ListingGateway.ListingBrief listingBrief() {
        return new ListingGateway.ListingBrief(
                LISTING_ID, LANDLORD_ID, "Phòng thử nghiệm", "phong-thu-nghiem", null,
                BigDecimal.valueOf(3_000_000), BigDecimal.valueOf(25), "Hà Nội",
                ListingStatus.ACTIVE, true, BigDecimal.valueOf(4), 2);
    }

    private UserGateway.UserBrief userBrief() {
        return new UserGateway.UserBrief(USER_ID, "Người thuê", null, "0900000000", Instant.now());
    }
}
