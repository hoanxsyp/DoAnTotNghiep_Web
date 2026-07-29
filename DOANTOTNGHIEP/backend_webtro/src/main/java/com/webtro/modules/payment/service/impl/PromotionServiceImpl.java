package com.webtro.modules.payment.service.impl;

import com.webtro.common.PageResponse;
import com.webtro.common.enums.AuditAction;
import com.webtro.common.enums.CouponDiscountType;
import com.webtro.common.enums.PaymentStatus;
import com.webtro.common.enums.SubscriptionStatus;
import com.webtro.constant.CacheName;
import com.webtro.constant.ConfigKey;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessException;
import com.webtro.exception.BusinessRuleException;
import com.webtro.exception.ConflictException;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.payment.dto.request.CouponRequest;
import com.webtro.modules.payment.dto.request.PackageRequest;
import com.webtro.modules.payment.dto.request.TogglePackageRequest;
import com.webtro.modules.payment.dto.request.ValidateCouponRequest;
import com.webtro.modules.payment.dto.response.CouponResponse;
import com.webtro.modules.payment.dto.response.CouponValidationResponse;
import com.webtro.modules.payment.dto.response.PromotionPackageResponse;
import com.webtro.modules.payment.dto.response.PromotionSubscriptionResponse;
import com.webtro.modules.payment.entity.Coupon;
import com.webtro.modules.payment.entity.Payment;
import com.webtro.modules.payment.entity.PromotionPackage;
import com.webtro.modules.payment.entity.PromotionSubscription;
import com.webtro.modules.payment.mapper.CouponMapper;
import com.webtro.modules.payment.mapper.PromotionPackageMapper;
import com.webtro.modules.payment.mapper.PromotionSubscriptionMapper;
import com.webtro.modules.payment.repository.CouponRepository;
import com.webtro.modules.payment.repository.PaymentRepository;
import com.webtro.modules.payment.repository.PromotionPackageRepository;
import com.webtro.modules.payment.repository.PromotionSubscriptionRepository;
import com.webtro.modules.payment.service.PromotionService;
import com.webtro.modules.payment.spi.AuditGateway;
import com.webtro.modules.payment.spi.ListingPromotionGateway;
import com.webtro.util.HtmlSanitizer;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Hiện thực nghiệp vụ khuyến mãi (gói dịch vụ, coupon, lượt đẩy) — canonical 4.9.1/2/8/9, 4.18.
 *
 * <p>Đổi giá/tắt gói KHÔNG ảnh hưởng giao dịch đã tạo: {@code payments.amount} là snapshot tại thời
 * điểm tạo đơn (xử lý ở {@code PaymentService}). {@code used_count} của coupon chỉ tăng khi callback
 * SUCCESS (mục 6). Trần {@code priority} lấy từ {@code promotion.max_priority} (không hardcode).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionServiceImpl implements PromotionService {

    private final PromotionPackageRepository packageRepository;
    private final CouponRepository couponRepository;
    private final PromotionSubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final PromotionPackageMapper packageMapper;
    private final CouponMapper couponMapper;
    private final PromotionSubscriptionMapper subscriptionMapper;
    private final SystemConfigService systemConfig;
    private final ListingPromotionGateway listingGateway;
    private final AuditGateway auditGateway;
    private final com.webtro.security.RateLimitService rateLimitService;

    // =========================== Gói dịch vụ ============================

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheName.PROMOTION_PACKAGES, key = "'active'")
    public List<PromotionPackageResponse> getActivePackages() {
        return packageRepository.findByIsActiveTrueAndDeletedAtIsNullOrderByDisplayOrderAsc()
                .stream().map(packageMapper::toPublic).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionPackageResponse getPackage(Long id) {
        return packageMapper.toPublic(loadPackage(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromotionPackageResponse> adminListPackages(boolean activeOnly) {
        List<PromotionPackage> packages = packageRepository.findAll().stream()
                .filter(p -> p.getDeletedAt() == null)
                .filter(p -> !activeOnly || Boolean.TRUE.equals(p.getIsActive()))
                .sorted(Comparator.comparing(PromotionPackage::getDisplayOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<PromotionPackageResponse> result = new ArrayList<>(packages.size());
        for (PromotionPackage p : packages) {
            long activeSubs = subscriptionRepository.findAll().stream()
                    .filter(s -> s.getDeletedAt() == null)
                    .filter(s -> p.getId().equals(s.getPackageId()))
                    .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE)
                    .count();
            BigDecimal revenue = paymentRepository.findAll().stream()
                    .filter(pay -> pay.getDeletedAt() == null)
                    .filter(pay -> p.getId().equals(pay.getPackageId()))
                    .filter(pay -> pay.getStatus() == PaymentStatus.SUCCESS)
                    .map(Payment::getFinalAmount)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            result.add(packageMapper.toAdmin(p, activeSubs, revenue));
        }
        return result;
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheName.PROMOTION_PACKAGES, allEntries = true)
    public PromotionPackageResponse createPackage(PackageRequest request, Long adminId) {
        String code = request.getCode().trim().toUpperCase();
        if (packageRepository.existsByCodeAndDeletedAtIsNull(code)) {
            throw new ConflictException(ErrorCode.PACKAGE_CODE_DUPLICATE,
                    "Mã gói \"" + code + "\" đã tồn tại");
        }
        int maxPriority = systemConfig.getInt(ConfigKey.PROMOTION_MAX_PRIORITY);
        validatePriority(request.getPriority(), maxPriority);

        PromotionPackage entity = PromotionPackage.builder()
                .code(code)
                .name(HtmlSanitizer.stripAllHtml(request.getName()))
                .description(request.getDescription() == null ? null
                        : HtmlSanitizer.stripAllHtml(request.getDescription()))
                .price(request.getPrice())
                .durationDays(request.getDurationDays())
                .priority(request.getPriority())
                .badgeLabel(request.getBadgeLabel() == null ? null
                        : HtmlSanitizer.stripAllHtml(request.getBadgeLabel()))
                .badgeColor(request.getBadgeColor())
                .displayOrder(request.getDisplayOrder() == null ? 0 : request.getDisplayOrder())
                .isActive(request.getActive() == null || request.getActive())
                .purchaseCount(0)
                .build();
        PromotionPackage saved = packageRepository.save(entity);
        auditGateway.record(AuditAction.PACKAGE_CHANGE, adminId, "PACKAGE", saved.getId(),
                saved.getName(), "Tạo gói dịch vụ " + saved.getCode());
        return packageMapper.toAdmin(saved, 0L, BigDecimal.ZERO);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheName.PROMOTION_PACKAGES, allEntries = true)
    public PromotionPackageResponse updatePackage(Long id, PackageRequest request, Long adminId) {
        PromotionPackage entity = loadPackage(id);
        int maxPriority = systemConfig.getInt(ConfigKey.PROMOTION_MAX_PRIORITY);
        validatePriority(request.getPriority(), maxPriority);

        // code bất biến (canonical 4.18.3) — bỏ qua request.code.
        entity.setName(HtmlSanitizer.stripAllHtml(request.getName()));
        entity.setDescription(request.getDescription() == null ? null
                : HtmlSanitizer.stripAllHtml(request.getDescription()));
        entity.setPrice(request.getPrice());
        entity.setDurationDays(request.getDurationDays());
        entity.setPriority(request.getPriority());
        entity.setBadgeLabel(request.getBadgeLabel() == null ? null
                : HtmlSanitizer.stripAllHtml(request.getBadgeLabel()));
        entity.setBadgeColor(request.getBadgeColor());
        if (request.getDisplayOrder() != null) {
            entity.setDisplayOrder(request.getDisplayOrder());
        }
        if (request.getActive() != null) {
            entity.setIsActive(request.getActive());
        }
        PromotionPackage saved = packageRepository.save(entity);
        auditGateway.record(AuditAction.PACKAGE_CHANGE, adminId, "PACKAGE", saved.getId(),
                saved.getName(), "Sửa gói dịch vụ " + saved.getCode());
        return packageMapper.toAdmin(saved, activeSubCount(saved.getId()), revenueOf(saved.getId()));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheName.PROMOTION_PACKAGES, allEntries = true)
    public PromotionPackageResponse togglePackage(Long id, TogglePackageRequest request, Long adminId) {
        PromotionPackage entity = loadPackage(id);
        entity.setIsActive(request.getActive());
        PromotionPackage saved = packageRepository.save(entity);
        auditGateway.record(AuditAction.PACKAGE_CHANGE, adminId, "PACKAGE", saved.getId(),
                saved.getName(),
                (request.getActive() ? "Bật bán gói: " : "Tắt bán gói: ")
                        + HtmlSanitizer.stripAllHtml(request.getReason()));
        return packageMapper.toAdmin(saved, activeSubCount(saved.getId()), revenueOf(saved.getId()));
    }

    // ============================== Coupon ==============================

    @Override
    @Transactional(readOnly = true)
    public CouponValidationResponse validateCoupon(ValidateCouponRequest request, Long userId) {
        rateLimitService.checkLimit("coupon_validate", String.valueOf(userId), 20,
                java.time.Duration.ofMinutes(1));
        PromotionPackage pkg = loadActivePackage(request.getPackageId());
        Coupon coupon = loadValidCoupon(request.getCode(), userId);
        BigDecimal original = pkg.getPrice();
        checkMinOrder(coupon, original);
        BigDecimal discount = computeDiscount(coupon, original);
        BigDecimal finalAmount = original.subtract(discount).max(BigDecimal.ZERO);
        return CouponValidationResponse.builder()
                .code(coupon.getCode())
                .valid(true)
                .description(coupon.getDescription())
                .discountType(coupon.getDiscountType().name())
                .discountValue(coupon.getDiscountValue())
                .originalAmount(original)
                .discountAmount(discount)
                .finalAmount(finalAmount)
                .validTo(coupon.getEndAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CouponResolution resolveCoupon(String code, Long userId, Long packageId, BigDecimal originalAmount) {
        if (code == null || code.isBlank()) {
            return new CouponResolution(null, null, BigDecimal.ZERO, originalAmount);
        }
        Coupon coupon = loadValidCoupon(code.trim().toUpperCase(), userId);
        checkMinOrder(coupon, originalAmount);
        BigDecimal discount = computeDiscount(coupon, originalAmount);
        BigDecimal finalAmount = originalAmount.subtract(discount).max(BigDecimal.ZERO);
        return new CouponResolution(coupon.getId(), coupon.getCode(), discount, finalAmount);
    }

    @Override
    @Transactional
    public void consumeCoupon(Long couponId) {
        if (couponId == null) {
            return;
        }
        couponRepository.findByIdAndDeletedAtIsNull(couponId).ifPresent(c -> {
            c.setUsedCount(c.getUsedCount() == null ? 1 : c.getUsedCount() + 1);
            couponRepository.save(c);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CouponResponse> adminListCoupons(String keyword, Boolean activeOnly,
                                                         Boolean validNow, Pageable pageable) {
        Instant now = Instant.now();
        Specification<Coupon> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.isNull(root.get("deletedAt")));
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toUpperCase() + "%";
                ps.add(cb.or(
                        cb.like(cb.upper(root.get("code")), like),
                        cb.like(cb.upper(root.get("description")), like)));
            }
            if (Boolean.TRUE.equals(activeOnly)) {
                ps.add(cb.isTrue(root.get("isActive")));
            }
            if (Boolean.TRUE.equals(validNow)) {
                ps.add(cb.lessThanOrEqualTo(root.get("startAt"), now));
                ps.add(cb.greaterThanOrEqualTo(root.get("endAt"), now));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<Coupon> page = couponRepository.findAll(spec, pageable);
        return PageResponse.from(page, couponMapper::toResponse);
    }

    @Override
    @Transactional
    public CouponResponse createCoupon(CouponRequest request, Long adminId) {
        String code = request.getCode().trim().toUpperCase();
        if (couponRepository.existsByCodeAndDeletedAtIsNull(code)) {
            throw new ConflictException(ErrorCode.COUPON_CODE_DUPLICATE,
                    "Mã coupon \"" + code + "\" đã tồn tại");
        }
        validateCouponRequest(request);
        Coupon entity = Coupon.builder()
                .code(code)
                .description(HtmlSanitizer.stripAllHtml(request.getDescription()))
                .discountType(request.getDiscountType())
                .discountValue(request.getDiscountValue())
                .maxDiscountAmount(request.getMaxDiscountAmount())
                .minOrderAmount(request.getMinOrderAmount() == null ? BigDecimal.ZERO : request.getMinOrderAmount())
                .usageLimit(request.getUsageLimit())
                .usedCount(0)
                .perUserLimit(request.getPerUserLimit() == null ? 1 : request.getPerUserLimit())
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .isActive(request.getActive() == null || request.getActive())
                .build();
        Coupon saved = couponRepository.save(entity);
        auditGateway.record(AuditAction.PACKAGE_CHANGE, adminId, "COUPON", saved.getId(),
                saved.getCode(), "Tạo mã khuyến mãi " + saved.getCode());
        return couponMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public CouponResponse updateCoupon(Long id, CouponRequest request, Long adminId) {
        Coupon entity = couponRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.COUPON_NOT_FOUND));
        validateCouponRequest(request);
        // code bất biến.
        entity.setDescription(HtmlSanitizer.stripAllHtml(request.getDescription()));
        entity.setDiscountType(request.getDiscountType());
        entity.setDiscountValue(request.getDiscountValue());
        entity.setMaxDiscountAmount(request.getMaxDiscountAmount());
        entity.setMinOrderAmount(request.getMinOrderAmount() == null ? BigDecimal.ZERO : request.getMinOrderAmount());
        entity.setUsageLimit(request.getUsageLimit());
        entity.setPerUserLimit(request.getPerUserLimit() == null ? 1 : request.getPerUserLimit());
        entity.setStartAt(request.getStartAt());
        entity.setEndAt(request.getEndAt());
        if (request.getActive() != null) {
            entity.setIsActive(request.getActive());
        }
        Coupon saved = couponRepository.save(entity);
        auditGateway.record(AuditAction.PACKAGE_CHANGE, adminId, "COUPON", saved.getId(),
                saved.getCode(), "Sửa mã khuyến mãi " + saved.getCode());
        return couponMapper.toResponse(saved);
    }

    // =========================== Subscription ===========================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PromotionSubscriptionResponse> getMySubscriptions(
            Long userId, List<SubscriptionStatus> statuses, Long listingId, Pageable pageable) {
        Specification<PromotionSubscription> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.isNull(root.get("deletedAt")));
            ps.add(cb.equal(root.get("userId"), userId));
            if (statuses != null && !statuses.isEmpty()) {
                ps.add(root.get("status").in(statuses));
            }
            if (listingId != null) {
                ps.add(cb.equal(root.get("listingId"), listingId));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<PromotionSubscription> page = subscriptionRepository.findAll(spec, pageable);
        return PageResponse.from(page, this::toSubscriptionResponse);
    }

    @Override
    @Transactional
    public int expireOverdueSubscriptions() {
        Instant now = Instant.now();
        List<PromotionSubscription> overdue = subscriptionRepository
                .findByStatusAndEndAtBeforeAndDeletedAtIsNull(SubscriptionStatus.ACTIVE, now);
        int count = 0;
        for (PromotionSubscription s : overdue) {
            s.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionRepository.save(s);
            try {
                // Chỉ gỡ cờ đẩy nếu tin không còn lượt đẩy ACTIVE nào khác.
                boolean stillPromoted = subscriptionRepository
                        .findByListingIdAndStatusAndDeletedAtIsNull(s.getListingId(), SubscriptionStatus.ACTIVE)
                        .stream().anyMatch(other -> !other.getId().equals(s.getId()));
                if (!stillPromoted) {
                    listingGateway.clearPromotion(s.getListingId());
                }
            } catch (RuntimeException e) {
                log.warn("Không gỡ được cờ đẩy tin {} khi hết hạn lượt đẩy {}: {}",
                        s.getListingId(), s.getId(), e.getMessage());
            }
            count++;
        }
        if (count > 0) {
            log.info("Đã chuyển {} lượt đẩy tin sang EXPIRED", count);
        }
        return count;
    }

    // ============================= Helper ==============================

    private PromotionSubscriptionResponse toSubscriptionResponse(PromotionSubscription s) {
        String packageName = packageRepository.findByIdAndDeletedAtIsNull(s.getPackageId())
                .map(PromotionPackage::getName).orElse(null);
        String badgeLabel = packageRepository.findByIdAndDeletedAtIsNull(s.getPackageId())
                .map(PromotionPackage::getBadgeLabel).orElse(null);
        String txnCode = paymentRepository.findByIdAndDeletedAtIsNull(s.getPaymentId())
                .map(Payment::getTransactionCode).orElse(null);
        BigDecimal amount = paymentRepository.findByIdAndDeletedAtIsNull(s.getPaymentId())
                .map(Payment::getFinalAmount).orElse(null);
        String listingTitle = null;
        try {
            listingTitle = listingGateway.getForPromotion(s.getListingId()).title();
        } catch (RuntimeException ignored) {
            // Tin có thể đã bị xóa mềm — bỏ qua tiêu đề.
        }
        return subscriptionMapper.toResponse(s, txnCode, listingTitle, packageName, badgeLabel, amount);
    }

    private PromotionPackage loadPackage(Long id) {
        return packageRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PACKAGE_NOT_FOUND));
    }

    private PromotionPackage loadActivePackage(Long id) {
        PromotionPackage pkg = loadPackage(id);
        if (!Boolean.TRUE.equals(pkg.getIsActive())) {
            throw new BusinessRuleException(ErrorCode.PACKAGE_INACTIVE);
        }
        return pkg;
    }

    private long activeSubCount(Long packageId) {
        return subscriptionRepository.findAll().stream()
                .filter(s -> s.getDeletedAt() == null)
                .filter(s -> packageId.equals(s.getPackageId()))
                .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE)
                .count();
    }

    private BigDecimal revenueOf(Long packageId) {
        return paymentRepository.findAll().stream()
                .filter(p -> p.getDeletedAt() == null)
                .filter(p -> packageId.equals(p.getPackageId()))
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                .map(Payment::getFinalAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void validatePriority(Integer priority, int maxPriority) {
        if (priority != null && priority > maxPriority) {
            throw new BusinessException(ErrorCode.PACKAGE_PRIORITY_EXCEEDED,
                    "Mức ưu tiên tối đa là " + maxPriority);
        }
    }

    private void validateCouponRequest(CouponRequest request) {
        if (request.getEndAt().isBefore(request.getStartAt())
                || request.getEndAt().equals(request.getStartAt())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Thời điểm kết thúc phải sau thời điểm bắt đầu");
        }
        if (request.getDiscountType() == CouponDiscountType.PERCENT) {
            BigDecimal v = request.getDiscountValue();
            if (v.compareTo(BigDecimal.ONE) < 0 || v.compareTo(new BigDecimal("100")) > 0) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "Phần trăm giảm phải trong khoảng 1..100");
            }
            if (request.getMaxDiscountAmount() == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "Coupon giảm theo phần trăm cần có trần giảm (maxDiscountAmount)");
            }
        }
    }

    /** Nạp coupon còn hiệu lực cho user: kiểm tồn tại, bật, cửa sổ thời gian, lượt dùng. */
    private Coupon loadValidCoupon(String code, Long userId) {
        Coupon coupon = couponRepository.findByCodeAndDeletedAtIsNull(code)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.COUPON_NOT_FOUND));
        if (!Boolean.TRUE.equals(coupon.getIsActive())) {
            throw new BusinessRuleException(ErrorCode.COUPON_INACTIVE);
        }
        Instant now = Instant.now();
        if (now.isBefore(coupon.getStartAt())) {
            throw new BusinessRuleException(ErrorCode.COUPON_NOT_STARTED);
        }
        if (now.isAfter(coupon.getEndAt())) {
            throw new BusinessRuleException(ErrorCode.COUPON_EXPIRED);
        }
        if (coupon.getUsageLimit() != null
                && coupon.getUsedCount() != null
                && coupon.getUsedCount() >= coupon.getUsageLimit()) {
            throw new BusinessRuleException(ErrorCode.COUPON_USAGE_EXCEEDED);
        }
        long usedByUser = countSuccessfulUsageByUser(coupon.getId(), userId);
        int perUser = coupon.getPerUserLimit() == null ? 1 : coupon.getPerUserLimit();
        if (usedByUser >= perUser) {
            throw new ConflictException(ErrorCode.COUPON_ALREADY_USED_BY_USER);
        }
        return coupon;
    }

    private long countSuccessfulUsageByUser(Long couponId, Long userId) {
        Specification<Payment> spec = (root, query, cb) -> cb.and(
                cb.isNull(root.get("deletedAt")),
                cb.equal(root.get("couponId"), couponId),
                cb.equal(root.get("userId"), userId),
                cb.equal(root.get("status"), PaymentStatus.SUCCESS));
        return paymentRepository.count(spec);
    }

    private void checkMinOrder(Coupon coupon, BigDecimal original) {
        BigDecimal min = coupon.getMinOrderAmount() == null ? BigDecimal.ZERO : coupon.getMinOrderAmount();
        if (original.compareTo(min) < 0) {
            throw new BusinessRuleException(ErrorCode.COUPON_NOT_APPLICABLE,
                    "Đơn tối thiểu để áp mã là " + min.toPlainString() + " đ");
        }
    }

    /** Số tiền giảm thực tế, không vượt số tiền gốc. */
    private BigDecimal computeDiscount(Coupon coupon, BigDecimal original) {
        BigDecimal discount;
        if (coupon.getDiscountType() == CouponDiscountType.PERCENT) {
            discount = original.multiply(coupon.getDiscountValue())
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            if (coupon.getMaxDiscountAmount() != null) {
                discount = discount.min(coupon.getMaxDiscountAmount());
            }
        } else {
            discount = coupon.getDiscountValue();
        }
        return discount.min(original).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}
