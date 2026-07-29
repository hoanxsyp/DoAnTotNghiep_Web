package com.webtro.modules.payment.gateway;

import lombok.Builder;
import lombok.Getter;

/** Kết quả khởi tạo thanh toán: URL chuyển hướng + mã giao dịch phía cổng. */
@Getter
@Builder
public class PaymentInitResult {

    /** URL để chuyển hướng người dùng tới trang thanh toán. */
    private final String paymentUrl;

    /** Mã giao dịch nội bộ (để đối soát). */
    private final String transactionCode;

    /** Mã tham chiếu phía cổng (nếu có). */
    private final String providerRef;
}
