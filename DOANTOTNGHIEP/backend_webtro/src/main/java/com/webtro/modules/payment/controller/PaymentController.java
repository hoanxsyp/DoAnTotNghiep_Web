package com.webtro.modules.payment.controller;

import com.webtro.common.ApiResponse;
import com.webtro.constant.PermissionCode;
import com.webtro.modules.payment.dto.request.CreatePaymentRequest;
import com.webtro.modules.payment.dto.request.PaymentCallbackRequest;
import com.webtro.modules.payment.dto.request.PromoteListingRequest;
import com.webtro.modules.payment.dto.response.PaymentCallbackResponse;
import com.webtro.modules.payment.dto.response.PaymentHistoryResponse;
import com.webtro.modules.payment.dto.response.PaymentResponse;
import com.webtro.modules.payment.service.PaymentService;
import com.webtro.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/**
 * API thanh toán mua gói đẩy tin (canonical 4.9.3–4.9.7, 4.9.10, mục 6).
 * Callback là endpoint công khai (bảo vệ bằng HMAC signature); các endpoint còn lại cần
 * {@code PAYMENT_VIEW_OWN}.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "09. Payment & Promotion", description = "Thanh toán, gói dịch vụ, khuyến mãi")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/payments")
    @PreAuthorize("hasAuthority('" + PermissionCode.PAYMENT_VIEW_OWN + "')")
    @Operation(summary = "Tạo giao dịch mua gói đẩy tin")
    public ResponseEntity<ApiResponse<PaymentResponse>> create(
            @Valid @RequestBody CreatePaymentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal CustomUserDetails user,
            HttpServletRequest http) {
        PaymentResponse data = paymentService.createPayment(request, user.getId(), idempotencyKey,
                clientIp(http));
        return ResponseEntity.created(URI.create("/api/payments/" + data.getId()))
                .body(ApiResponse.success(data, "Đã tạo đơn thanh toán. Vui lòng hoàn tất trong thời gian cho phép."));
    }

    @PostMapping("/listings/{id}/promote")
    @PreAuthorize("hasAuthority('" + PermissionCode.PAYMENT_VIEW_OWN + "')")
    @Operation(summary = "Mua gói đẩy tin cho một tin (đường tắt)")
    public ResponseEntity<ApiResponse<PaymentResponse>> promote(
            @PathVariable Long id,
            @Valid @RequestBody PromoteListingRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal CustomUserDetails user,
            HttpServletRequest http) {
        PaymentResponse data = paymentService.promote(id, request, user.getId(), idempotencyKey,
                clientIp(http));
        return ResponseEntity.created(URI.create("/api/payments/" + data.getId()))
                .body(ApiResponse.success(data, "Đã tạo đơn thanh toán. Vui lòng hoàn tất trong thời gian cho phép."));
    }

    @GetMapping("/payments/{id}")
    @PreAuthorize("hasAnyAuthority('" + PermissionCode.PAYMENT_VIEW_OWN + "','" + PermissionCode.PAYMENT_MANAGE + "')")
    @Operation(summary = "Chi tiết một giao dịch")
    public ResponseEntity<ApiResponse<PaymentResponse>> detail(
            @PathVariable Long id, @AuthenticationPrincipal CustomUserDetails user) {
        boolean canManage = user.hasPermission(PermissionCode.PAYMENT_MANAGE);
        PaymentResponse data = paymentService.getPayment(id, user.getId(), canManage);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy chi tiết giao dịch thành công"));
    }

    @GetMapping("/payments/my")
    @PreAuthorize("hasAuthority('" + PermissionCode.PAYMENT_VIEW_OWN + "')")
    @Operation(summary = "Lịch sử thanh toán của tôi")
    public ResponseEntity<ApiResponse<PaymentHistoryResponse>> myPayments(
            @RequestParam(required = false) List<com.webtro.common.enums.PaymentStatus> status,
            @RequestParam(required = false) Long listingId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails user) {
        PaymentService.MyPaymentFilter filter = new PaymentService.MyPaymentFilter(status, listingId, from, to);
        PaymentHistoryResponse data = paymentService.getMyPayments(user.getId(), filter, pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy lịch sử thanh toán thành công"));
    }

    @PostMapping("/payments/{id}/cancel")
    @PreAuthorize("hasAuthority('" + PermissionCode.PAYMENT_VIEW_OWN + "')")
    @Operation(summary = "Hủy giao dịch đang chờ thanh toán")
    public ResponseEntity<ApiResponse<PaymentResponse>> cancel(
            @PathVariable Long id, @AuthenticationPrincipal CustomUserDetails user) {
        PaymentResponse data = paymentService.cancelPayment(id, user.getId());
        return ResponseEntity.ok(ApiResponse.success(data, "Đã hủy giao dịch"));
    }

    @PostMapping("/payments/callback")
    @Operation(summary = "Callback từ cổng thanh toán (công khai, xác thực HMAC)")
    public ResponseEntity<ApiResponse<PaymentCallbackResponse>> callback(
            @Valid @RequestBody PaymentCallbackRequest request) {
        PaymentCallbackResponse data = paymentService.handleCallback(request);
        String message = data.isAlreadyProcessed()
                ? "Callback đã được xử lý trước đó" : "Đã xử lý callback thanh toán";
        return ResponseEntity.ok(ApiResponse.success(data, message));
    }

    /** Lấy IP client thật (ưu tiên X-Forwarded-For do đứng sau nginx). */
    private String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
