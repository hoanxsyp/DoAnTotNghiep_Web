package com.webtro.modules.payment.service;

import com.webtro.common.enums.PaymentMethod;
import com.webtro.common.enums.PaymentStatus;
import com.webtro.modules.payment.dto.request.CreatePaymentRequest;
import com.webtro.modules.payment.dto.request.PaymentCallbackRequest;
import com.webtro.modules.payment.dto.request.PromoteListingRequest;
import com.webtro.modules.payment.dto.request.RefundPaymentRequest;
import com.webtro.modules.payment.dto.response.PaymentCallbackResponse;
import com.webtro.modules.payment.dto.response.PaymentHistoryResponse;
import com.webtro.modules.payment.dto.response.PaymentResponse;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Nghiệp vụ thanh toán mua gói đẩy tin (canonical 4.9, mục 6, {@code [§3.14]}, {@code [§8.2]}).
 *
 * <p>Luồng chính: tạo đơn {@code PENDING} với mã giao dịch duy nhất → cổng thanh toán → callback có
 * chữ ký → kích hoạt gói (tạo {@code PromotionSubscription} + bật cờ đẩy trên tin) →
 * {@code PaymentSucceededEvent}. Idempotent theo {@code Idempotency-Key} (tạo đơn) và theo
 * {@code transactionCode} (callback).
 */
public interface PaymentService {

    /** Bộ lọc lịch sử thanh toán của tôi. */
    record MyPaymentFilter(List<PaymentStatus> statuses, Long listingId, LocalDate from, LocalDate to) {
    }

    /** Bộ lọc danh sách giao dịch phía quản trị. */
    record AdminPaymentFilter(List<PaymentStatus> statuses, List<PaymentMethod> methods, Long userId,
                              Long listingId, Long packageId, String transactionCode,
                              BigDecimal amountFrom, BigDecimal amountTo, LocalDate from, LocalDate to) {
    }

    /**
     * Tạo giao dịch mua gói đẩy tin ({@code POST /api/payments}).
     *
     * @param request        thông tin đơn
     * @param userId         chủ trọ đang đăng nhập
     * @param idempotencyKey header {@code Idempotency-Key} (UUID) — chống tạo trùng
     * @param clientIp       IP client (lưu để đối soát)
     * @return đơn vừa tạo kèm {@code paymentUrl}
     */
    PaymentResponse createPayment(CreatePaymentRequest request, Long userId, String idempotencyKey,
                                  String clientIp);

    /**
     * Mua gói đẩy tin theo đường tắt ({@code POST /api/listings/{id}/promote}) — alias của
     * {@link #createPayment} với {@code listingId} lấy từ path.
     */
    PaymentResponse promote(Long listingId, PromoteListingRequest request, Long userId,
                            String idempotencyKey, String clientIp);

    /**
     * Chi tiết một giao dịch ({@code GET /api/payments/{id}}).
     *
     * @param canManage người gọi có {@code PAYMENT_MANAGE} không (xem mọi giao dịch + mã cổng)
     */
    PaymentResponse getPayment(Long id, Long currentUserId, boolean canManage);

    /** Lịch sử thanh toán của tôi ({@code GET /api/payments/my}). */
    PaymentHistoryResponse getMyPayments(Long userId, MyPaymentFilter filter, Pageable pageable);

    /** Hủy đơn đang {@code PENDING} ({@code POST /api/payments/{id}/cancel}). */
    PaymentResponse cancelPayment(Long id, Long userId);

    /**
     * Xử lý callback từ cổng ({@code POST /api/payments/callback}) — công khai, idempotent,
     * chống replay, kiểm khớp số tiền.
     */
    PaymentCallbackResponse handleCallback(PaymentCallbackRequest request);

    // ------------------------- Quản trị (PAYMENT_MANAGE) -------------------------

    /** Danh sách giao dịch có lọc ({@code GET /api/admin/payments}). */
    PaymentHistoryResponse adminListPayments(AdminPaymentFilter filter, Pageable pageable);

    /** Chi tiết giao dịch phía quản trị ({@code GET /api/admin/payments/{id}}). */
    PaymentResponse adminGetPayment(Long id);

    /** Đánh dấu hoàn tiền thủ công ({@code PUT /api/admin/payments/{id}/refund}). */
    PaymentResponse refundPayment(Long id, RefundPaymentRequest request, Long adminId);

    /** Đối soát một giao dịch với cổng ({@code POST /api/admin/payments/{id}/reconcile}). */
    PaymentCallbackResponse reconcilePayment(Long id, Long adminId);
}
