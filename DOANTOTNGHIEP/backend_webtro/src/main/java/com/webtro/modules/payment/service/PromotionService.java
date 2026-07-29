package com.webtro.modules.payment.service;

import com.webtro.common.PageResponse;
import com.webtro.common.enums.SubscriptionStatus;
import com.webtro.modules.payment.dto.request.CouponRequest;
import com.webtro.modules.payment.dto.request.PackageRequest;
import com.webtro.modules.payment.dto.request.TogglePackageRequest;
import com.webtro.modules.payment.dto.request.ValidateCouponRequest;
import com.webtro.modules.payment.dto.response.CouponResponse;
import com.webtro.modules.payment.dto.response.CouponValidationResponse;
import com.webtro.modules.payment.dto.response.PromotionPackageResponse;
import com.webtro.modules.payment.dto.response.PromotionSubscriptionResponse;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

/**
 * Nghiệp vụ khuyến mãi: gói dịch vụ ({@code promotion_packages}), mã giảm giá ({@code coupons}) và
 * lượt đẩy đã kích hoạt ({@code promotion_subscriptions}) — canonical 4.9.1/2/8/9, 4.18.
 *
 * <p>Các phương thức {@code resolveCoupon}/{@code consumeCoupon} là hợp đồng nội bộ module để
 * {@link PaymentService} dùng lại đúng một logic áp mã (canonical §3 luật 6, không nhân đôi).
 */
public interface PromotionService {

    /** Kết quả áp mã cho một đơn: id coupon (null nếu không áp), số giảm, số phải trả. */
    record CouponResolution(Long couponId, String couponCode, BigDecimal discountAmount,
                            BigDecimal finalAmount) {
    }

    // ----------------------------- Gói dịch vụ -----------------------------

    /** Danh sách gói đang bật, sắp theo {@code display_order} (công khai, có cache). */
    List<PromotionPackageResponse> getActivePackages();

    /** Chi tiết một gói (công khai). Ném {@code PACKAGE_NOT_FOUND} nếu không có. */
    PromotionPackageResponse getPackage(Long id);

    /** Danh sách gói phía quản trị (kể cả đã tắt) + số liệu thống kê. */
    List<PromotionPackageResponse> adminListPackages(boolean activeOnly);

    /** Tạo gói ({@code PACKAGE_MANAGE}). */
    PromotionPackageResponse createPackage(PackageRequest request, Long adminId);

    /** Sửa gói ({@code code} bất biến). */
    PromotionPackageResponse updatePackage(Long id, PackageRequest request, Long adminId);

    /** Bật/tắt bán gói (không xóa cứng). */
    PromotionPackageResponse togglePackage(Long id, TogglePackageRequest request, Long adminId);

    // ------------------------------- Coupon --------------------------------

    /** Kiểm tra mã cho một gói ({@code POST /api/coupons/validate}) — chỉ tính, không tiêu mã. */
    CouponValidationResponse validateCoupon(ValidateCouponRequest request, Long userId);

    /**
     * Áp mã khi tạo đơn: kiểm hiệu lực + lượt dùng + phạm vi gói + đơn tối thiểu, trả số tiền giảm.
     * Ném các lỗi {@code COUPON_*} nếu không hợp lệ. Trả {@link CouponResolution} rỗng nếu
     * {@code code} null/blank.
     */
    CouponResolution resolveCoupon(String code, Long userId, Long packageId, BigDecimal originalAmount);

    /** Tăng {@code used_count} của coupon khi thanh toán thành công. */
    void consumeCoupon(Long couponId);

    /** Danh sách coupon phía quản trị. */
    PageResponse<CouponResponse> adminListCoupons(String keyword, Boolean activeOnly, Boolean validNow,
                                                  Pageable pageable);

    /** Tạo coupon ({@code PACKAGE_MANAGE}). */
    CouponResponse createCoupon(CouponRequest request, Long adminId);

    /** Sửa coupon ({@code code} bất biến). */
    CouponResponse updateCoupon(Long id, CouponRequest request, Long adminId);

    // ---------------------------- Subscription -----------------------------

    /** Gói đã mua của tôi ({@code GET /api/promotion-subscriptions/my}). */
    PageResponse<PromotionSubscriptionResponse> getMySubscriptions(Long userId,
                                                                   List<SubscriptionStatus> statuses,
                                                                   Long listingId, Pageable pageable);

    /**
     * Chuyển các lượt đẩy {@code ACTIVE} đã quá {@code end_at} sang {@code EXPIRED} và gỡ cờ đẩy trên
     * tin tương ứng. Do {@code PromotionExpiryJob} (phase scheduler) gọi định kỳ.
     *
     * @return số lượt vừa chuyển sang hết hạn
     */
    int expireOverdueSubscriptions();
}
