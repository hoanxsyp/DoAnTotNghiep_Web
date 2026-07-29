package com.webtro.modules.payment.mapper;

import com.webtro.modules.payment.dto.response.PaymentResponse;
import com.webtro.modules.payment.dto.response.PromotionSubscriptionResponse;
import com.webtro.modules.payment.entity.Payment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Ánh xạ {@link Payment} → DTO (thủ công, Builder). {@code listingTitle}/{@code packageName}/
 * {@code couponCode}/{@code subscription} do service bổ sung (nằm ở entity khác). {@code originalAmount}
 * = {@code amount} của entity (giá gốc snapshot); {@code amount} DTO = {@code finalAmount} (thực trả).
 */
@Component
public class PaymentMapper {

    /**
     * Bản đầy đủ cho chi tiết / lịch sử.
     *
     * @param includeGatewayId có kèm mã giao dịch phía cổng không (chỉ PAYMENT_MANAGE)
     */
    public PaymentResponse toResponse(Payment p, String listingTitle, String packageName,
                                      String couponCode, PromotionSubscriptionResponse subscription,
                                      boolean includeGatewayId) {
        boolean refundable = com.webtro.common.enums.PaymentStatus.SUCCESS.equals(p.getStatus())
                && p.getRefundedAt() == null;
        return PaymentResponse.builder()
                .id(p.getId())
                .transactionCode(p.getTransactionCode())
                .gatewayTransactionId(includeGatewayId ? p.getGatewayTxnRef() : null)
                .userId(p.getUserId())
                .listingId(p.getListingId())
                .listingTitle(listingTitle)
                .packageId(p.getPackageId())
                .packageName(packageName)
                .originalAmount(p.getAmount())
                .discountAmount(p.getDiscountAmount())
                .amount(p.getFinalAmount())
                .couponCode(couponCode)
                .paymentMethod(p.getPaymentMethod() == null ? null : p.getPaymentMethod().name())
                .status(p.getStatus() == null ? null : p.getStatus().name())
                .statusLabel(p.getStatus() == null ? null : p.getStatus().getLabel())
                .failureReason(p.getFailureReason())
                .subscription(subscription)
                .refundable(refundable)
                .refundAmount(p.getRefundAmount())
                .createdAt(p.getCreatedAt())
                .paidAt(p.getPaidAt())
                .expiresAt(p.getExpiresAt())
                .build();
    }

    /**
     * Bản trả về khi tạo đơn: kèm {@code paymentUrl} chuyển hướng cổng.
     */
    public PaymentResponse toCreated(Payment p, String listingTitle, String packageName,
                                     String couponCode, String paymentUrl) {
        return PaymentResponse.builder()
                .id(p.getId())
                .transactionCode(p.getTransactionCode())
                .userId(p.getUserId())
                .listingId(p.getListingId())
                .listingTitle(listingTitle)
                .packageId(p.getPackageId())
                .packageName(packageName)
                .originalAmount(p.getAmount())
                .discountAmount(p.getDiscountAmount())
                .amount(p.getFinalAmount())
                .couponCode(couponCode)
                .paymentMethod(p.getPaymentMethod() == null ? null : p.getPaymentMethod().name())
                .status(p.getStatus() == null ? null : p.getStatus().name())
                .statusLabel(p.getStatus() == null ? null : p.getStatus().getLabel())
                .paymentUrl(paymentUrl)
                .createdAt(p.getCreatedAt())
                .expiresAt(p.getExpiresAt())
                .build();
    }

    /** Bản rút gọn cho danh sách (lịch sử/admin): không nhúng subscription. */
    public PaymentResponse toListItem(Payment p, String listingTitle, String packageName,
                                      boolean includeGatewayId) {
        return toResponse(p, listingTitle, packageName, null, null, includeGatewayId);
    }

    /** Giá trị an toàn cho số tiền null. */
    public BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
