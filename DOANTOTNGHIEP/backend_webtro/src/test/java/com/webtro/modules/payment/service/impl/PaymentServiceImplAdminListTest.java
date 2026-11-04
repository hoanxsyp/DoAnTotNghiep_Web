package com.webtro.modules.payment.service.impl;

import com.webtro.config.properties.AppProperties;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.payment.dto.response.PaymentResponse;
import com.webtro.modules.payment.entity.Payment;
import com.webtro.modules.payment.gateway.PaymentGateway;
import com.webtro.modules.payment.mapper.PaymentMapper;
import com.webtro.modules.payment.mapper.PromotionSubscriptionMapper;
import com.webtro.modules.payment.repository.CouponRepository;
import com.webtro.modules.payment.repository.PaymentRepository;
import com.webtro.modules.payment.repository.PromotionPackageRepository;
import com.webtro.modules.payment.repository.PromotionSubscriptionRepository;
import com.webtro.modules.payment.service.PaymentService;
import com.webtro.modules.payment.service.PromotionService;
import com.webtro.modules.payment.spi.AuditGateway;
import com.webtro.modules.payment.spi.ListingPromotionGateway;
import com.webtro.modules.payment.spi.PaymentUserGateway;
import com.webtro.modules.notification.service.NotificationService;
import com.webtro.security.RateLimitService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.StringRedisTemplate;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplAdminListTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PromotionPackageRepository packageRepository;
    @Mock private PromotionSubscriptionRepository subscriptionRepository;
    @Mock private CouponRepository couponRepository;
    @Mock private PaymentMapper paymentMapper;
    @Mock private PromotionSubscriptionMapper subscriptionMapper;
    @Mock private PromotionService promotionService;
    @Mock private PaymentGateway paymentGateway;
    @Mock private ListingPromotionGateway listingGateway;
    @Mock private SystemConfigService systemConfig;
    @Mock private NotificationService notificationService;
    @Mock private RateLimitService rateLimitService;
    @Mock private AuditGateway auditGateway;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private AppProperties appProperties;
    @Mock private PaymentUserGateway paymentUserGateway;

    @InjectMocks private PaymentServiceImpl service;

    @Test
    void searchesByBuyerAndEnrichesAdminRows() {
        PageRequest pageable = PageRequest.of(0, 20);
        Payment payment = Payment.builder().userId(7L).transactionCode("WT-001").build();
        payment.setId(1L);
        PaymentResponse mapped = PaymentResponse.builder().id(1L).userId(7L).build();
        when(paymentUserGateway.searchBuyerIds("Nguyễn An")).thenReturn(Set.of(7L));
        when(paymentRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Payment>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(payment), pageable, 1));
        when(paymentMapper.toListItem(payment, null, null, true)).thenReturn(mapped);
        when(paymentUserGateway.findBuyers(Set.of(7L))).thenReturn(Map.of(
                7L, new PaymentUserGateway.Buyer(7L, "Nguyễn An", "an@example.test")));

        var result = service.adminListPayments(filter("  Nguyễn An  "), pageable);

        assertThat(result.getPage().getItems()).singleElement().satisfies(item -> {
            assertThat(item.getBuyerName()).isEqualTo("Nguyễn An");
            assertThat(item.getBuyerEmail()).isEqualTo("an@example.test");
        });
        verify(paymentUserGateway).searchBuyerIds("Nguyễn An");
    }

    @Test
    void buildsKeywordPredicateWithMatchingBuyerIds() {
        executeKeywordSpecification("Nguyễn An", Set.of(7L), false);
    }

    @Test
    void buildsKeywordPredicateWithoutMatchingBuyerIds() {
        executeKeywordSpecification("không có", Set.of(), true);
    }

    @Test
    void skipsBuyerSearchForBlankKeywordAndKeepsMissingBuyerEmpty() {
        PageRequest pageable = PageRequest.of(0, 20);
        Payment payment = Payment.builder().userId(8L).transactionCode("WT-002").build();
        PaymentResponse mapped = PaymentResponse.builder().id(2L).userId(8L).build();
        when(paymentRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Payment>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(payment), pageable, 1));
        when(paymentMapper.toListItem(payment, null, null, true)).thenReturn(mapped);
        when(paymentUserGateway.findBuyers(Set.of(8L))).thenReturn(Map.of());

        var result = service.adminListPayments(filter("  "), pageable);

        executeCapturedBaseSpecification(pageable);

        assertThat(result.getPage().getItems()).singleElement().satisfies(item -> {
            assertThat(item.getBuyerName()).isNull();
            assertThat(item.getBuyerEmail()).isNull();
        });
        verify(paymentUserGateway, never()).searchBuyerIds(any());
    }

    @Test
    void acceptsNullKeywordWithoutBuyerSearch() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(paymentRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Payment>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.adminListPayments(filter(null), pageable);

        executeCapturedBaseSpecification(pageable);

        verify(paymentUserGateway, never()).searchBuyerIds(any());
    }

    @Test
    void enrichesAdminPaymentDetail() {
        Payment payment = Payment.builder().userId(7L).transactionCode("WT-003").build();
        payment.setId(3L);
        PaymentResponse mapped = PaymentResponse.builder().id(3L).userId(7L).build();
        when(paymentRepository.findByIdAndDeletedAtIsNull(3L)).thenReturn(Optional.of(payment));
        when(subscriptionRepository.findByPaymentIdAndDeletedAtIsNull(3L)).thenReturn(Optional.empty());
        when(paymentMapper.toResponse(eq(payment), any(), any(), any(), any(), eq(true))).thenReturn(mapped);
        when(paymentUserGateway.findBuyers(Set.of(7L))).thenReturn(Map.of(
                7L, new PaymentUserGateway.Buyer(7L, "Nguyễn An", "an@example.test")));

        PaymentResponse result = service.adminGetPayment(3L);

        assertThat(result.getBuyerName()).isEqualTo("Nguyễn An");
        assertThat(result.getBuyerEmail()).isEqualTo("an@example.test");
    }

    @Test
    void keepsBuyerMetadataOutOfTenantPaymentDetail() {
        Payment payment = Payment.builder().userId(7L).transactionCode("WT-004").build();
        payment.setId(4L);
        PaymentResponse mapped = PaymentResponse.builder().id(4L).userId(7L).build();
        when(paymentRepository.findByIdAndDeletedAtIsNull(4L)).thenReturn(Optional.of(payment));
        when(subscriptionRepository.findByPaymentIdAndDeletedAtIsNull(4L)).thenReturn(Optional.empty());
        when(paymentMapper.toResponse(eq(payment), any(), any(), any(), any(), eq(false))).thenReturn(mapped);

        PaymentResponse result = service.getPayment(4L, 7L, false);

        assertThat(result.getBuyerName()).isNull();
        verify(paymentUserGateway, never()).findBuyers(any());
    }

    @Test
    void keepsBuyerMetadataOutOfTenantPaymentHistory() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(paymentRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Payment>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.getMyPayments(7L, new PaymentService.MyPaymentFilter(null, null, null, null), pageable);

        verify(paymentUserGateway, never()).findBuyers(any());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void executeKeywordSpecification(String keyword, Set<Long> buyerIds, boolean expectDisjunction) {
        PageRequest pageable = PageRequest.of(0, 20);
        when(paymentUserGateway.searchBuyerIds(keyword)).thenReturn(buyerIds);
        when(paymentRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Payment>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.adminListPayments(filter(keyword), pageable);

        ArgumentCaptor<Specification<Payment>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(paymentRepository).findAll(captor.capture(), eq(pageable));
        Root<Payment> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<Object> deletedAt = mock(Path.class);
        Path<String> transactionCode = mock(Path.class);
        Path<Object> userId = mock(Path.class);
        Expression<String> loweredCode = mock(Expression.class);
        Predicate predicate = mock(Predicate.class);
        when(root.get("deletedAt")).thenReturn((Path) deletedAt);
        when(root.get("transactionCode")).thenReturn((Path) transactionCode);
        when(cb.isNull(deletedAt)).thenReturn(predicate);
        when(cb.lower(transactionCode)).thenReturn(loweredCode);
        when(cb.like(eq(loweredCode), anyString())).thenReturn(predicate);
        if (expectDisjunction) {
            when(cb.disjunction()).thenReturn(predicate);
        } else {
            when(root.get("userId")).thenReturn((Path) userId);
            when(userId.in(buyerIds)).thenReturn(predicate);
        }
        when(cb.or(any(Predicate.class), any(Predicate.class))).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        captor.getValue().toPredicate(root, query, cb);

        if (expectDisjunction) {
            verify(cb).disjunction();
        } else {
            verify(userId).in(buyerIds);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void executeCapturedBaseSpecification(PageRequest pageable) {
        ArgumentCaptor<Specification<Payment>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(paymentRepository).findAll(captor.capture(), eq(pageable));
        Root<Payment> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<Object> deletedAt = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        when(root.get("deletedAt")).thenReturn((Path) deletedAt);
        when(cb.isNull(deletedAt)).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        captor.getValue().toPredicate(root, query, cb);
    }

    private PaymentService.AdminPaymentFilter filter(String keyword) {
        return new PaymentService.AdminPaymentFilter(
                null, null, null, null, null, null, keyword,
                null, null, null, null);
    }
}
