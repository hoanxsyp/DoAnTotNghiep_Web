package com.webtro.modules.interaction.service;

import com.webtro.modules.interaction.repository.ReviewRepository;
import com.webtro.modules.interaction.spi.ListingGateway;
import com.webtro.modules.interaction.spi.UserGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Mục 10 của docs/DANH_GIA_THEO_PHONG_DOI_CHIEU.md: thứ tự khoá và cách tính lại điểm. */
@ExtendWith(MockitoExtension.class)
class ReviewRatingRecalculatorTest {

    private static final long UNIT_ID = 7L;
    private static final long OWNER_ID = 3L;

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private ListingGateway listingGateway;
    @Mock
    private UserGateway userGateway;

    private ReviewRatingRecalculator recalculator;

    @BeforeEach
    void setUp() {
        recalculator = new ReviewRatingRecalculator(reviewRepository, listingGateway, userGateway);
    }

    @Test
    void locksLandlordProfileBeforeUnitToAvoidDeadlock() {
        when(listingGateway.getUnitBrief(UNIT_ID)).thenReturn(
                new ListingGateway.RentalUnitBrief(UNIT_ID, OWNER_ID, "P101", null, BigDecimal.ZERO, 0, false));
        when(userGateway.lockLandlordProfile(OWNER_ID)).thenReturn(true);

        ReviewRatingRecalculator.Scope scope = recalculator.lock(UNIT_ID);

        InOrder order = inOrder(userGateway, listingGateway);
        order.verify(userGateway).lockLandlordProfile(OWNER_ID);
        order.verify(listingGateway).lockRentalUnitForRating(UNIT_ID);
        assertEquals(OWNER_ID, scope.ownerId());
    }

    @Test
    void recalculatesUnitThenCopiesThenLandlordFromScratch() {
        when(reviewRepository.countVisibleByUnitLocking(UNIT_ID)).thenReturn(3L);
        when(reviewRepository.sumVisibleRatingByUnitLocking(UNIT_ID)).thenReturn(13L); // 4.333…
        when(reviewRepository.countVisibleByOwnerLocking(OWNER_ID)).thenReturn(8L);
        when(reviewRepository.sumVisibleRatingByOwnerLocking(OWNER_ID)).thenReturn(30L); // 3.75

        ReviewRatingRecalculator.Result result =
                recalculator.recalc(new ReviewRatingRecalculator.Scope(UNIT_ID, OWNER_ID, true));

        assertEquals(new BigDecimal("4.33"), result.unitAverage());
        assertEquals(3, result.unitCount());
        assertEquals(new BigDecimal("3.75"), result.landlordAverage());
        verify(listingGateway).applyUnitRating(UNIT_ID, new BigDecimal("4.33"), 3);
        verify(userGateway).updateLandlordReviewAggregate(OWNER_ID, new BigDecimal("3.75"), 8);
    }

    @Test
    void ownerWithoutLandlordProfileOnlyGetsUnitScore() {
        when(reviewRepository.countVisibleByUnitLocking(UNIT_ID)).thenReturn(0L);
        when(reviewRepository.sumVisibleRatingByUnitLocking(UNIT_ID)).thenReturn(0L);

        ReviewRatingRecalculator.Result result =
                recalculator.recalc(new ReviewRatingRecalculator.Scope(UNIT_ID, OWNER_ID, false));

        assertEquals(new BigDecimal("0.00"), result.unitAverage());
        assertNull(result.landlordAverage());
        verify(listingGateway).applyUnitRating(UNIT_ID, new BigDecimal("0.00"), 0);
        verify(userGateway, never()).updateLandlordReviewAggregate(anyLong(), any(), anyInt());
    }

    @Test
    void averageRoundsHalfUp() {
        assertEquals(new BigDecimal("4.67"), ReviewRatingRecalculator.average(14, 3));
        assertEquals(new BigDecimal("2.50"), ReviewRatingRecalculator.average(5, 2));
        assertEquals(new BigDecimal("0.00"), ReviewRatingRecalculator.average(0, 0));
    }
}
