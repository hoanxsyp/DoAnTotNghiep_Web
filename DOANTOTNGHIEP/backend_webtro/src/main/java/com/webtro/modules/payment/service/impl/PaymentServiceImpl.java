package com.webtro.modules.payment.service.impl;

import com.webtro.common.PageResponse;
import com.webtro.common.enums.ListingStatus;
import com.webtro.common.enums.NotificationType;
import com.webtro.common.enums.PaymentMethod;
import com.webtro.common.enums.PaymentStatus;
import com.webtro.common.enums.SubscriptionStatus;
import com.webtro.common.event.PaymentSucceededEvent;
import com.webtro.config.properties.AppProperties;
import com.webtro.constant.ConfigKey;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessException;
import com.webtro.exception.BusinessRuleException;
import com.webtro.exception.ConflictException;
import com.webtro.exception.ForbiddenException;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.payment.dto.request.CreatePaymentRequest;
import com.webtro.modules.payment.dto.request.PaymentCallbackRequest;
import com.webtro.modules.payment.dto.request.PromoteListingRequest;
import com.webtro.modules.payment.dto.request.RefundPaymentRequest;
import com.webtro.modules.payment.dto.response.PaymentCallbackResponse;
import com.webtro.modules.payment.dto.response.PaymentHistoryResponse;
import com.webtro.modules.payment.dto.response.PaymentResponse;
import com.webtro.modules.payment.dto.response.PromotionSubscriptionResponse;
import com.webtro.modules.payment.entity.Coupon;
import com.webtro.modules.payment.entity.Payment;
import com.webtro.modules.payment.entity.PromotionPackage;
import com.webtro.modules.payment.entity.PromotionSubscription;
import com.webtro.modules.payment.gateway.PaymentCallbackResult;
import com.webtro.modules.payment.gateway.PaymentGateway;
import com.webtro.modules.payment.gateway.PaymentInitResult;
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
import com.webtro.common.enums.AuditAction;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Hiện thực nghiệp vụ thanh toán mua gói đẩy tin (canonical 4.9, mục 6, {@code [§3.14]}, {@code [§8.2]}).
 *
 * <p>Điểm mấu chốt: đơn tạo ở {@code PENDING} với {@code transactionCode} duy nhất; chỉ khi callback
 * {@code SUCCESS} có chữ ký hợp lệ + khớp số tiền mới kích hoạt gói (tạo {@code PromotionSubscription}
 * + bật cờ đẩy trên tin qua {@link ListingPromotionGateway}) + tiêu coupon + phát
 * {@link PaymentSucceededEvent}. Callback idempotent theo {@code transactionCode}, chống replay bằng
 * nonce + khóa Redis.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final String TXN_PREFIX = "WT";
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final DateTimeFormatter YYYYMMDD =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Trạng thái tin cho phép mua gói đẩy (§3.14: Active hoặc Pending). */
    private static final Set<ListingStatus> PROMOTABLE = EnumSet.of(ListingStatus.ACTIVE, ListingStatus.PENDING);

    private static final String IDEM_PREFIX = "payment:idem:";
    private static final String IDEM_IN_PROGRESS = "IN_PROGRESS";
    private static final String NONCE_PREFIX = "callback:nonce:";
    private static final String LOCK_PREFIX = "payment:lock:";

    private final PaymentRepository paymentRepository;
    private final PromotionPackageRepository packageRepository;
    private final PromotionSubscriptionRepository subscriptionRepository;
    private final CouponRepository couponRepository;
    private final PaymentMapper paymentMapper;
    private final PromotionSubscriptionMapper subscriptionMapper;
    private final PromotionService promotionService;
    private final PaymentGateway paymentGateway;
    private final ListingPromotionGateway listingGateway;
    private final SystemConfigService systemConfig;
    private final com.webtro.modules.notification.service.NotificationService notificationService;
    private final com.webtro.security.RateLimitService rateLimitService;
    private final AuditGateway auditGateway;
    private final ApplicationEventPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;
    private final AppProperties appProperties;

    // =========================== Tạo giao dịch ===========================

    @Override
    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request, Long userId,
                                         String idempotencyKey, String clientIp) {
        return doCreate(request.getListingId(), request.getPackageId(), request.getPaymentMethod(),
                request.getCouponCode(), request.getReturnUrl(), userId, idempotencyKey, clientIp);
    }

    @Override
    @Transactional
    public PaymentResponse promote(Long listingId, PromoteListingRequest request, Long userId,
                                   String idempotencyKey, String clientIp) {
        return doCreate(listingId, request.getPackageId(), request.getPaymentMethod(),
                request.getCouponCode(), request.getReturnUrl(), userId, idempotencyKey, clientIp);
    }

    private PaymentResponse doCreate(Long listingId, Long packageId, PaymentMethod method,
                                     String couponCode, String returnUrl, Long userId,
                                     String idempotencyKey, String clientIp) {
        rateLimitService.checkLimit("payment_create", String.valueOf(userId), 20, Duration.ofHours(1));

        String idemKey = validateIdempotencyKey(idempotencyKey);
        validateReturnUrl(returnUrl);

        // 1. Chốt chống trùng: đặt sentinel IN_PROGRESS; nếu key đã tồn tại thì trả kết quả cũ.
        String redisIdem = IDEM_PREFIX + userId + ":" + idemKey;
        int expiryMinutes = systemConfig.getInt(ConfigKey.PAYMENT_ORDER_EXPIRY_MINUTES);
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(redisIdem, IDEM_IN_PROGRESS, expiryMinutes + 5L, TimeUnit.MINUTES);
        if (Boolean.FALSE.equals(acquired)) {
            String stored = redisTemplate.opsForValue().get(redisIdem);
            if (stored == null || IDEM_IN_PROGRESS.equals(stored)) {
                throw new ConflictException(ErrorCode.IDEMPOTENCY_KEY_IN_PROGRESS);
            }
            // Đã có giao dịch cho key này -> trả lại (idempotent).
            Payment existing = paymentRepository.findByTransactionCodeAndDeletedAtIsNull(stored)
                    .orElseThrow(() -> new ConflictException(ErrorCode.IDEMPOTENCY_KEY_REUSED));
            return buildCreatedResponse(existing, null);
        }

        try {
            // 2. Kiểm tra tin: tồn tại, chủ sở hữu, trạng thái.
            ListingPromotionGateway.PromotableListing listing = listingGateway.getForPromotion(listingId);
            if (!userId.equals(listing.ownerId())) {
                throw new ForbiddenException(ErrorCode.LISTING_FORBIDDEN);
            }
            if (listing.status() == ListingStatus.LOCKED) {
                throw new BusinessRuleException(ErrorCode.LISTING_LOCKED_CANNOT_PROMOTE);
            }
            if (!PROMOTABLE.contains(listing.status())) {
                throw new BusinessRuleException(ErrorCode.LISTING_NOT_PROMOTABLE);
            }

            // 3. Không mua chồng khi tin đang có gói đẩy ACTIVE.
            boolean hasActive = !subscriptionRepository
                    .findByListingIdAndStatusAndDeletedAtIsNull(listingId, SubscriptionStatus.ACTIVE).isEmpty();
            if (hasActive) {
                throw new ConflictException(ErrorCode.SUBSCRIPTION_ALREADY_ACTIVE);
            }

            // 4. Gói phải tồn tại + đang bán.
            PromotionPackage pkg = packageRepository.findByIdAndDeletedAtIsNull(packageId)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PACKAGE_NOT_FOUND));
            if (!Boolean.TRUE.equals(pkg.getIsActive())) {
                throw new BusinessRuleException(ErrorCode.PACKAGE_INACTIVE);
            }

            // 5. Áp coupon (nếu có) — cùng logic với /coupons/validate.
            BigDecimal original = pkg.getPrice();
            PromotionService.CouponResolution coupon =
                    promotionService.resolveCoupon(couponCode, userId, packageId, original);

            // 6. Tạo Payment PENDING với mã giao dịch duy nhất (snapshot giá).
            Instant now = Instant.now();
            Payment payment = Payment.builder()
                    .userId(userId)
                    .listingId(listingId)
                    .packageId(packageId)
                    .couponId(coupon.couponId())
                    .amount(original)
                    .discountAmount(coupon.discountAmount())
                    .finalAmount(coupon.finalAmount())
                    .currency("VND")
                    .paymentMethod(method)
                    .transactionCode(generateUniqueTransactionCode())
                    .status(PaymentStatus.PENDING)
                    .expiresAt(now.plus(Duration.ofMinutes(expiryMinutes)))
                    .clientIp(clientIp)
                    .build();
            payment = paymentRepository.save(payment);

            // 7. Gọi cổng thanh toán để lấy URL chuyển hướng.
            PaymentInitResult init = paymentGateway.createPayment(
                    payment.getTransactionCode(), payment.getFinalAmount(),
                    "Đẩy tin: " + pkg.getName());
            payment.setGatewayTxnRef(init.getProviderRef());
            payment = paymentRepository.save(payment);

            // 8. Ghi transactionCode vào idem key để lần gọi lại trả về cùng đơn.
            redisTemplate.opsForValue().set(redisIdem, payment.getTransactionCode(),
                    expiryMinutes + 5L, TimeUnit.MINUTES);

            return buildCreatedResponse(payment, init.getPaymentUrl());
        } catch (RuntimeException e) {
            // Thất bại -> giải phóng sentinel để user thử lại.
            safeDelete(redisIdem);
            throw e;
        }
    }

    // ============================ Xem giao dịch ==========================

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long id, Long currentUserId, boolean canManage) {
        Payment payment = loadPayment(id);
        if (!canManage && !payment.getUserId().equals(currentUserId)) {
            throw new ForbiddenException(ErrorCode.PAYMENT_FORBIDDEN);
        }
        return buildDetailResponse(payment, canManage);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentHistoryResponse getMyPayments(Long userId, MyPaymentFilter filter, Pageable pageable) {
        Specification<Payment> base = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.isNull(root.get("deletedAt")));
            ps.add(cb.equal(root.get("userId"), userId));
            addCommonFilters(ps, root, cb,
                    filter.statuses(), null, filter.listingId(), null, null,
                    null, null, filter.from(), filter.to());
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return pageWithSummary(base, pageable, false);
    }

    // ============================== Hủy đơn ==============================

    @Override
    @Transactional
    public PaymentResponse cancelPayment(Long id, Long userId) {
        Payment payment = loadPayment(id);
        if (!payment.getUserId().equals(userId)) {
            throw new ForbiddenException(ErrorCode.PAYMENT_FORBIDDEN);
        }
        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            throw new ConflictException(ErrorCode.PAYMENT_ALREADY_CANCELLED);
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new BusinessRuleException(ErrorCode.PAYMENT_NOT_PENDING);
        }
        payment.setStatus(PaymentStatus.CANCELLED);
        paymentRepository.save(payment);
        return buildDetailResponse(payment, false);
    }

    // ============================= Callback ==============================

    @Override
    @Transactional
    public PaymentCallbackResponse handleCallback(PaymentCallbackRequest request) {
        // 1. Chống replay theo thời gian.
        long maxSkew = systemConfig.getLong(ConfigKey.PAYMENT_CALLBACK_MAX_SKEW_SECONDS);
        long skew = Math.abs(Instant.now().getEpochSecond() - request.getTimestamp());
        if (skew > maxSkew) {
            throw new BusinessException(ErrorCode.PAYMENT_CALLBACK_EXPIRED);
        }

        // 2. Xác thực chữ ký qua cổng.
        PaymentCallbackResult verified = paymentGateway.verifyCallback(toGatewayParams(request));
        if (!verified.isSignatureValid()) {
            log.warn("Callback chữ ký không hợp lệ cho transactionCode={}", request.getTransactionCode());
            throw new BusinessException(ErrorCode.PAYMENT_SIGNATURE_INVALID);
        }

        // 3. Nonce guard (chống replay).
        Boolean nonceOk = redisTemplate.opsForValue()
                .setIfAbsent(NONCE_PREFIX + request.getNonce(), "1", 600, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(nonceOk)) {
            throw new ConflictException(ErrorCode.PAYMENT_CALLBACK_REPLAY);
        }

        // 4. Khóa theo transactionCode (chống xử lý song song).
        String lockKey = LOCK_PREFIX + request.getTransactionCode();
        Boolean lockOk = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 30, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(lockOk)) {
            throw new ConflictException(ErrorCode.PAYMENT_CALLBACK_REPLAY);
        }
        try {
            return processCallback(request);
        } finally {
            safeDelete(lockKey);
        }
    }

    private PaymentCallbackResponse processCallback(PaymentCallbackRequest request) {
        Payment payment = paymentRepository
                .findByTransactionCodeAndDeletedAtIsNull(request.getTransactionCode())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PAYMENT_NOT_FOUND));

        // Kiểm khớp số tiền trước mọi thứ (chống can thiệp).
        if (payment.getFinalAmount().compareTo(request.getAmount()) != 0) {
            log.warn("Callback lệch số tiền: đơn {} = {}, callback = {}",
                    payment.getTransactionCode(), payment.getFinalAmount(), request.getAmount());
            throw new BusinessRuleException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        PaymentStatus target = request.getStatus();

        // Idempotent: đơn đã ở trạng thái cuối.
        if (payment.getStatus() != PaymentStatus.PENDING) {
            if (payment.getStatus() == target
                    || (target == PaymentStatus.SUCCESS && payment.getStatus() == PaymentStatus.REFUNDED)) {
                return alreadyProcessed(payment);
            }
            log.warn("Callback khác kết quả cho đơn {} (đang {}, callback {})",
                    payment.getTransactionCode(), payment.getStatus(), target);
            throw new ConflictException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }

        PaymentStatus previous = payment.getStatus();
        payment.setGatewayTxnRef(request.getGatewayTransactionId());
        payment.setGatewayResponse(buildGatewayResponseJson(request));

        if (target == PaymentStatus.SUCCESS) {
            return handleSuccess(payment, request, previous);
        }
        if (target == PaymentStatus.CANCELLED) {
            payment.setStatus(PaymentStatus.CANCELLED);
            paymentRepository.save(payment);
            return callbackResponse(payment, previous, false, null, null, false);
        }
        // FAILED
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(request.getResponseMessage());
        paymentRepository.save(payment);
        notificationService.notifyUser(payment.getUserId(), NotificationType.PAYMENT_FAILED,
                "Thanh toán thất bại",
                "Giao dịch " + payment.getTransactionCode() + " không thành công. Vui lòng thử lại.");
        return callbackResponse(payment, previous, false, null, null, true);
    }

    private PaymentCallbackResponse handleSuccess(Payment payment, PaymentCallbackRequest request,
                                                  PaymentStatus previous) {
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(request.getPaidAt() != null ? request.getPaidAt() : Instant.now());
        paymentRepository.save(payment);

        // Kích hoạt gói: tạo subscription + bật cờ đẩy trên tin.
        PromotionPackage pkg = packageRepository.findById(payment.getPackageId()).orElse(null);
        int durationDays = pkg != null && pkg.getDurationDays() != null ? pkg.getDurationDays() : 7;
        int maxPriority = systemConfig.getInt(ConfigKey.PROMOTION_MAX_PRIORITY);
        int priority = Math.min(pkg != null && pkg.getPriority() != null ? pkg.getPriority() : 0, maxPriority);

        Instant start = payment.getPaidAt();
        Instant end = start.plus(Duration.ofDays(durationDays));

        PromotionSubscription sub = subscriptionRepository
                .findByPaymentIdAndDeletedAtIsNull(payment.getId())
                .orElseGet(() -> PromotionSubscription.builder()
                        .paymentId(payment.getId())
                        .listingId(payment.getListingId())
                        .packageId(payment.getPackageId())
                        .userId(payment.getUserId())
                        .build());
        sub.setPriority(priority);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setStartAt(start);
        sub.setEndAt(end);
        sub = subscriptionRepository.save(sub);

        if (payment.getListingId() != null) {
            listingGateway.applyPromotion(payment.getListingId(), priority, end);
        }

        // Tiêu coupon (chỉ khi thành công).
        String couponCode = null;
        if (payment.getCouponId() != null) {
            promotionService.consumeCoupon(payment.getCouponId());
            couponCode = couponRepository.findByIdAndDeletedAtIsNull(payment.getCouponId())
                    .map(Coupon::getCode).orElse(null);
        }

        // Tăng bộ đếm lượt mua của gói.
        if (pkg != null) {
            pkg.setPurchaseCount((pkg.getPurchaseCount() == null ? 0 : pkg.getPurchaseCount()) + 1);
            packageRepository.save(pkg);
        }

        // Phát event + thông báo.
        eventPublisher.publishEvent(
                new PaymentSucceededEvent(payment.getId(), payment.getUserId(), payment.getListingId()));
        notificationService.notifyUser(payment.getUserId(), NotificationType.PAYMENT_SUCCESS,
                "Thanh toán thành công",
                "Gói đẩy tin đã được kích hoạt và có hiệu lực đến "
                        + end.atZone(ZoneOffset.UTC).toLocalDate() + ".");

        return callbackResponse(payment, previous, false, subscriptionMapper.toBrief(sub), couponCode, true);
    }

    // ============================ Quản trị ==============================

    @Override
    @Transactional(readOnly = true)
    public PaymentHistoryResponse adminListPayments(AdminPaymentFilter filter, Pageable pageable) {
        Specification<Payment> base = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.isNull(root.get("deletedAt")));
            addCommonFilters(ps, root, cb,
                    filter.statuses(), filter.methods(), filter.listingId(), filter.userId(),
                    filter.packageId(), filter.amountFrom(), filter.amountTo(), filter.from(), filter.to());
            if (filter.transactionCode() != null && !filter.transactionCode().isBlank()) {
                ps.add(cb.equal(root.get("transactionCode"), filter.transactionCode().trim()));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return pageWithSummary(base, pageable, true);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse adminGetPayment(Long id) {
        return buildDetailResponse(loadPayment(id), true);
    }

    @Override
    @Transactional
    public PaymentResponse refundPayment(Long id, RefundPaymentRequest request, Long adminId) {
        Payment payment = loadPayment(id);
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new BusinessRuleException(ErrorCode.PAYMENT_REFUND_NOT_ALLOWED);
        }
        if (payment.getRefundedAt() != null || payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new ConflictException(ErrorCode.PAYMENT_ALREADY_REFUNDED);
        }
        BigDecimal refundAmount = request.getRefundAmount() != null
                ? request.getRefundAmount() : payment.getFinalAmount();
        if (refundAmount.compareTo(payment.getFinalAmount()) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Số tiền hoàn không được vượt số tiền đã thanh toán");
        }
        boolean cancelSub = request.getCancelSubscription() == null || request.getCancelSubscription();

        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundedAt(Instant.now());
        payment.setRefundAmount(refundAmount);
        payment.setRefundNote(com.webtro.util.HtmlSanitizer.stripAllHtml(request.getReason()));
        payment.setRefundedBy(adminId);
        paymentRepository.save(payment);

        if (cancelSub) {
            subscriptionRepository.findByPaymentIdAndDeletedAtIsNull(payment.getId()).ifPresent(sub -> {
                sub.setStatus(SubscriptionStatus.CANCELLED);
                sub.setCancelledReason(com.webtro.util.HtmlSanitizer.stripAllHtml(request.getReason()));
                subscriptionRepository.save(sub);
                if (sub.getListingId() != null) {
                    listingGateway.clearPromotion(sub.getListingId());
                }
            });
        }

        auditGateway.record(AuditAction.PAYMENT_REFUND, adminId, "PAYMENT", payment.getId(),
                payment.getTransactionCode(),
                "Hoàn tiền " + refundAmount.toPlainString() + " đ: "
                        + com.webtro.util.HtmlSanitizer.stripAllHtml(request.getReason()));

        // Không có NotificationType chuyên cho hoàn tiền trong canonical §5 -> báo qua kênh thanh toán.
        notificationService.notifyUser(payment.getUserId(), NotificationType.PAYMENT_SUCCESS,
                "Đã hoàn tiền giao dịch",
                "Giao dịch " + payment.getTransactionCode() + " đã được hoàn "
                        + refundAmount.toPlainString() + " đ.");

        return buildDetailResponse(payment, true);
    }

    @Override
    @Transactional
    public PaymentCallbackResponse reconcilePayment(Long id, Long adminId) {
        Payment payment = loadPayment(id);
        PaymentStatus previous = payment.getStatus();

        // Cổng SANDBOX không expose truy vấn trạng thái; đối soát cục bộ theo quy tắc hết hạn đơn.
        if (payment.getStatus() == PaymentStatus.PENDING
                && payment.getExpiresAt() != null
                && payment.getExpiresAt().isBefore(Instant.now())) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Quá hạn thanh toán, đối soát chuyển FAILED");
            paymentRepository.save(payment);
            log.info("Đối soát đơn {} quá hạn -> FAILED", payment.getTransactionCode());
        }
        PromotionSubscription sub = subscriptionRepository
                .findByPaymentIdAndDeletedAtIsNull(payment.getId()).orElse(null);
        return callbackResponse(payment, previous, previous == payment.getStatus(),
                sub == null ? null : subscriptionMapper.toBrief(sub), null, false);
    }

    // ============================= Helpers ==============================

    private PaymentResponse buildCreatedResponse(Payment payment, String paymentUrl) {
        String listingTitle = safeListingTitle(payment.getListingId());
        String packageName = packageRepository.findById(payment.getPackageId())
                .map(PromotionPackage::getName).orElse(null);
        String couponCode = payment.getCouponId() == null ? null
                : couponRepository.findByIdAndDeletedAtIsNull(payment.getCouponId())
                .map(Coupon::getCode).orElse(null);
        return paymentMapper.toCreated(payment, listingTitle, packageName, couponCode, paymentUrl);
    }

    private PaymentResponse buildDetailResponse(Payment payment, boolean includeGatewayId) {
        String listingTitle = safeListingTitle(payment.getListingId());
        String packageName = packageRepository.findById(payment.getPackageId())
                .map(PromotionPackage::getName).orElse(null);
        String couponCode = payment.getCouponId() == null ? null
                : couponRepository.findByIdAndDeletedAtIsNull(payment.getCouponId())
                .map(Coupon::getCode).orElse(null);
        PromotionSubscriptionResponse subscription = subscriptionRepository
                .findByPaymentIdAndDeletedAtIsNull(payment.getId())
                .map(subscriptionMapper::toBrief).orElse(null);
        return paymentMapper.toResponse(payment, listingTitle, packageName, couponCode,
                subscription, includeGatewayId);
    }

    private PaymentHistoryResponse pageWithSummary(Specification<Payment> base, Pageable pageable,
                                                   boolean includeGatewayId) {
        Pageable remapped = remapSort(pageable);
        Page<Payment> page = paymentRepository.findAll(base, remapped);
        PageResponse<PaymentResponse> mapped = PageResponse.from(page,
                p -> paymentMapper.toListItem(p, safeListingTitle(p.getListingId()),
                        packageRepository.findById(p.getPackageId())
                                .map(PromotionPackage::getName).orElse(null),
                        includeGatewayId));
        return PaymentHistoryResponse.builder()
                .page(mapped)
                .summary(buildSummary(base))
                .build();
    }

    private PaymentHistoryResponse.Summary buildSummary(Specification<Payment> base) {
        BigDecimal totalPaid = paymentRepository.findAll(withStatus(base, PaymentStatus.SUCCESS)).stream()
                .map(Payment::getFinalAmount).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return PaymentHistoryResponse.Summary.builder()
                .totalPaid(totalPaid)
                .successCount(paymentRepository.count(withStatus(base, PaymentStatus.SUCCESS)))
                .failedCount(paymentRepository.count(withStatus(base, PaymentStatus.FAILED)))
                .pendingCount(paymentRepository.count(withStatus(base, PaymentStatus.PENDING)))
                .refundedCount(paymentRepository.count(withStatus(base, PaymentStatus.REFUNDED)))
                .cancelledCount(paymentRepository.count(withStatus(base, PaymentStatus.CANCELLED)))
                .build();
    }

    private Specification<Payment> withStatus(Specification<Payment> base, PaymentStatus status) {
        return base.and((root, query, cb) -> cb.equal(root.get("status"), status));
    }

    private void addCommonFilters(List<Predicate> ps, jakarta.persistence.criteria.Root<Payment> root,
                                  jakarta.persistence.criteria.CriteriaBuilder cb,
                                  List<PaymentStatus> statuses, List<PaymentMethod> methods,
                                  Long listingId, Long userId, Long packageId,
                                  BigDecimal amountFrom, BigDecimal amountTo,
                                  LocalDate from, LocalDate to) {
        if (statuses != null && !statuses.isEmpty()) {
            ps.add(root.get("status").in(statuses));
        }
        if (methods != null && !methods.isEmpty()) {
            ps.add(root.get("paymentMethod").in(methods));
        }
        if (listingId != null) {
            ps.add(cb.equal(root.get("listingId"), listingId));
        }
        if (userId != null) {
            ps.add(cb.equal(root.get("userId"), userId));
        }
        if (packageId != null) {
            ps.add(cb.equal(root.get("packageId"), packageId));
        }
        if (amountFrom != null) {
            ps.add(cb.greaterThanOrEqualTo(root.get("finalAmount"), amountFrom));
        }
        if (amountTo != null) {
            ps.add(cb.lessThanOrEqualTo(root.get("finalAmount"), amountTo));
        }
        if (from != null) {
            ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay(ZoneOffset.UTC).toInstant()));
        }
        if (to != null) {
            ps.add(cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
        }
    }

    /** Remap sort "amount" -> "finalAmount"; chỉ cho phép createdAt/paidAt/amount. */
    private Pageable remapSort(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return pageable;
        }
        List<Sort.Order> orders = new ArrayList<>();
        for (Sort.Order o : pageable.getSort()) {
            String prop = switch (o.getProperty()) {
                case "amount" -> "finalAmount";
                case "createdAt", "paidAt", "finalAmount" -> o.getProperty();
                default -> throw new BusinessException(ErrorCode.INVALID_SORT_FIELD,
                        "Không thể sắp xếp theo trường " + o.getProperty());
            };
            orders.add(new Sort.Order(o.getDirection(), prop));
        }
        return org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), Sort.by(orders));
    }

    private PaymentCallbackResponse alreadyProcessed(Payment payment) {
        PromotionSubscriptionResponse sub = subscriptionRepository
                .findByPaymentIdAndDeletedAtIsNull(payment.getId())
                .map(subscriptionMapper::toBrief).orElse(null);
        return PaymentCallbackResponse.builder()
                .transactionCode(payment.getTransactionCode())
                .paymentId(payment.getId())
                .status(payment.getStatus().name())
                .previousStatus(payment.getStatus().name())
                .alreadyProcessed(true)
                .subscription(sub)
                .userNotified(false)
                .processedAt(Instant.now())
                .build();
    }

    private PaymentCallbackResponse callbackResponse(Payment payment, PaymentStatus previous,
                                                     boolean alreadyProcessed,
                                                     PromotionSubscriptionResponse sub,
                                                     String couponConsumed, boolean notified) {
        return PaymentCallbackResponse.builder()
                .transactionCode(payment.getTransactionCode())
                .paymentId(payment.getId())
                .status(payment.getStatus().name())
                .previousStatus(previous == null ? null : previous.name())
                .alreadyProcessed(alreadyProcessed)
                .subscription(sub)
                .couponConsumed(couponConsumed)
                .userNotified(notified)
                .processedAt(Instant.now())
                .build();
    }

    private Payment loadPayment(Long id) {
        return paymentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    private String safeListingTitle(Long listingId) {
        if (listingId == null) {
            return null;
        }
        try {
            return listingGateway.getForPromotion(listingId).title();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String validateIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        try {
            return UUID.fromString(key.trim()).toString();
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
        }
    }

    /** Chống open redirect: returnUrl phải tuyệt đối và cùng origin với cấu hình cổng (§11.1). */
    private void validateReturnUrl(String returnUrl) {
        if (returnUrl == null || returnUrl.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Thiếu URL nhận kết quả thanh toán");
        }
        java.net.URI uri;
        try {
            uri = java.net.URI.create(returnUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "URL nhận kết quả không hợp lệ");
        }
        if (uri.getScheme() == null || uri.getHost() == null
                || !(uri.getScheme().equals("http") || uri.getScheme().equals("https"))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "URL nhận kết quả phải là http(s) tuyệt đối");
        }
        String configured = appProperties.getPayment().getReturnUrl();
        if (configured != null && !configured.isBlank()) {
            java.net.URI base = java.net.URI.create(configured);
            boolean sameOrigin = uri.getScheme().equalsIgnoreCase(base.getScheme())
                    && uri.getHost().equalsIgnoreCase(base.getHost())
                    && uri.getPort() == base.getPort();
            if (!sameOrigin) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "URL nhận kết quả không nằm trong danh sách cho phép");
            }
        }
    }

    private String generateUniqueTransactionCode() {
        for (int i = 0; i < 5; i++) {
            String code = TXN_PREFIX + YYYYMMDD.format(Instant.now()) + randomBase32(8);
            if (paymentRepository.findByTransactionCodeAndDeletedAtIsNull(code).isEmpty()) {
                return code;
            }
        }
        // Cực hiếm khi trùng 5 lần -> thêm hậu tố thời gian nano.
        return TXN_PREFIX + YYYYMMDD.format(Instant.now()) + randomBase32(6)
                + Long.toString(System.nanoTime(), 32).toUpperCase();
    }

    private String randomBase32(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(BASE32.charAt(RANDOM.nextInt(BASE32.length())));
        }
        return sb.toString();
    }

    private Map<String, String> toGatewayParams(PaymentCallbackRequest request) {
        Map<String, String> params = new HashMap<>();
        params.put("txn", request.getTransactionCode());
        params.put("amount", request.getAmount().toPlainString());
        params.put("sig", request.getSignature());
        params.put("status", request.getStatus() == PaymentStatus.SUCCESS ? "success" : "failed");
        params.put("gatewayTxnId", request.getGatewayTransactionId());
        return params;
    }

    private String buildGatewayResponseJson(PaymentCallbackRequest request) {
        return "{\"gatewayTransactionId\":\"" + safe(request.getGatewayTransactionId())
                + "\",\"responseCode\":\"" + safe(request.getResponseCode())
                + "\",\"responseMessage\":\"" + safe(request.getResponseMessage())
                + "\",\"nonce\":\"" + safe(request.getNonce()) + "\"}";
    }

    private String safe(String s) {
        return s == null ? "" : s.replace("\"", "'");
    }

    private void safeDelete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException e) {
            log.warn("Không xóa được Redis key {}: {}", key, e.getMessage());
        }
    }
}
