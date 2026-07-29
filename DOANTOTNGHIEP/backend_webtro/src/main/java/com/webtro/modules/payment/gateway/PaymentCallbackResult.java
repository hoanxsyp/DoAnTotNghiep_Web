package com.webtro.modules.payment.gateway;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/** Kết quả callback thanh toán đã xác thực chữ ký. */
@Getter
@Builder
public class PaymentCallbackResult {

    /** Chữ ký hợp lệ hay không — nếu false thì KHÔNG được xử lý (có thể là giả mạo). */
    private final boolean signatureValid;

    /** Mã giao dịch nội bộ. */
    private final String transactionCode;

    /** Giao dịch thành công hay thất bại (theo cổng báo). */
    private final boolean success;

    /** Số tiền cổng xác nhận — phải khớp số tiền đơn để chống can thiệp. */
    private final BigDecimal amount;

    /** Mã tham chiếu phía cổng. */
    private final String providerRef;

    /** Thông điệp từ cổng. */
    private final String message;
}
