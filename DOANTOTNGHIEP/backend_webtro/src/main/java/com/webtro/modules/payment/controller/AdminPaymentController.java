package com.webtro.modules.payment.controller;

import com.webtro.common.ApiResponse;
import com.webtro.common.enums.PaymentMethod;
import com.webtro.common.enums.PaymentStatus;
import com.webtro.constant.PermissionCode;
import com.webtro.modules.payment.dto.request.RefundPaymentRequest;
import com.webtro.modules.payment.dto.response.PaymentCallbackResponse;
import com.webtro.modules.payment.dto.response.PaymentHistoryResponse;
import com.webtro.modules.payment.dto.response.PaymentResponse;
import com.webtro.modules.payment.service.PaymentService;
import com.webtro.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * API quản trị giao dịch thanh toán (canonical 4.18.5–4.18.8). Quyền {@code PAYMENT_MANAGE}
 * (chỉ Admin — Moderator KHÔNG có, canonical §1.2/§4.2).
 */
@RestController
@RequestMapping("/api/admin/payments")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + PermissionCode.PAYMENT_MANAGE + "')")
@Tag(name = "18. Admin - Payment", description = "Quản trị gói dịch vụ, thanh toán, coupon")
public class AdminPaymentController {

    private final PaymentService paymentService;

    @GetMapping
    @Operation(summary = "Danh sách giao dịch có lọc")
    public ResponseEntity<ApiResponse<PaymentHistoryResponse>> list(
            @RequestParam(required = false) List<PaymentStatus> status,
            @RequestParam(required = false) List<PaymentMethod> paymentMethod,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long listingId,
            @RequestParam(required = false) Long packageId,
            @RequestParam(required = false) String transactionCode,
            @RequestParam(required = false) BigDecimal amountFrom,
            @RequestParam(required = false) BigDecimal amountTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PaymentService.AdminPaymentFilter filter = new PaymentService.AdminPaymentFilter(
                status, paymentMethod, userId, listingId, packageId, transactionCode,
                amountFrom, amountTo, from, to);
        PaymentHistoryResponse data = paymentService.adminListPayments(filter, pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy danh sách giao dịch thành công"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết giao dịch (kèm mã cổng)")
    public ResponseEntity<ApiResponse<PaymentResponse>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                paymentService.adminGetPayment(id), "Lấy chi tiết giao dịch thành công"));
    }

    @PutMapping("/{id}/refund")
    @Operation(summary = "Đánh dấu hoàn tiền thủ công")
    public ResponseEntity<ApiResponse<PaymentResponse>> refund(
            @PathVariable Long id, @Valid @RequestBody RefundPaymentRequest request) {
        PaymentResponse data = paymentService.refundPayment(id, request, currentUserId());
        return ResponseEntity.ok(ApiResponse.success(data,
                "Đã đánh dấu hoàn tiền cho giao dịch " + data.getTransactionCode()));
    }

    @PostMapping("/{id}/reconcile")
    @Operation(summary = "Đối soát một giao dịch với cổng")
    public ResponseEntity<ApiResponse<PaymentCallbackResponse>> reconcile(@PathVariable Long id) {
        PaymentCallbackResponse data = paymentService.reconcilePayment(id, currentUserId());
        return ResponseEntity.ok(ApiResponse.success(data, "Đã đối soát giao dịch"));
    }

    private Long currentUserId() {
        return SecurityUtils.getCurrentUserId().orElse(null);
    }
}
