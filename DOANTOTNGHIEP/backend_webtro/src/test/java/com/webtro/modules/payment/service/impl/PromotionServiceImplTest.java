package com.webtro.modules.payment.service.impl;

import com.webtro.common.enums.SubscriptionStatus;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.payment.entity.PromotionSubscription;
import com.webtro.modules.payment.mapper.CouponMapper;
import com.webtro.modules.payment.mapper.PromotionSubscriptionMapper;
import com.webtro.modules.payment.mapper.PromotionPackageMapper;
import com.webtro.modules.payment.repository.CouponRepository;
import com.webtro.modules.payment.repository.PaymentRepository;
import com.webtro.modules.payment.repository.PromotionPackageRepository;
import com.webtro.modules.payment.repository.PromotionSubscriptionRepository;
import com.webtro.modules.payment.spi.AuditGateway;
import com.webtro.modules.payment.spi.ListingPromotionGateway;
import com.webtro.security.RateLimitService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromotionServiceImplTest {

    @Test
    void expireOverdueSubscriptionsClearsListingPromotion() {
        PromotionPackageRepository packageRepository = mock(PromotionPackageRepository.class);
        CouponRepository couponRepository = mock(CouponRepository.class);
        PromotionSubscriptionRepository subscriptionRepository = mock(PromotionSubscriptionRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PromotionPackageMapper packageMapper = mock(PromotionPackageMapper.class);
        CouponMapper couponMapper = mock(CouponMapper.class);
        PromotionSubscriptionMapper subscriptionMapper = mock(PromotionSubscriptionMapper.class);
        SystemConfigService systemConfig = mock(SystemConfigService.class);
        ListingPromotionGateway listingGateway = mock(ListingPromotionGateway.class);
        AuditGateway auditGateway = mock(AuditGateway.class);
        RateLimitService rateLimitService = mock(RateLimitService.class);

        PromotionServiceImpl service = new PromotionServiceImpl(
                packageRepository, couponRepository, subscriptionRepository, paymentRepository,
                packageMapper, couponMapper, subscriptionMapper, systemConfig, listingGateway,
                auditGateway, rateLimitService);

        PromotionSubscription overdue = PromotionSubscription.builder()
                .listingId(321L)
                .status(SubscriptionStatus.ACTIVE)
                .startAt(Instant.now().minusSeconds(7200))
                .endAt(Instant.now().minusSeconds(3600))
                .build();
        overdue.setId(99L);

        when(subscriptionRepository.findByStatusAndEndAtBeforeAndDeletedAtIsNull(
                eq(SubscriptionStatus.ACTIVE), any(Instant.class))).thenReturn(List.of(overdue));
        when(subscriptionRepository.findByListingIdAndStatusAndDeletedAtIsNull(
                321L, SubscriptionStatus.ACTIVE)).thenReturn(List.of(overdue));

        assertEquals(1, service.expireOverdueSubscriptions());
        assertEquals(SubscriptionStatus.EXPIRED, overdue.getStatus());
        verify(subscriptionRepository).save(overdue);
        verify(listingGateway).clearPromotion(321L);
    }
}
