package com.webtro.modules.payment.gateway;

import java.math.BigDecimal;

/**
 * Trừu tượng cổng thanh toán (canonical mục 3). Cô lập cổng ngoài để có thể thay SANDBOX bằng
 * VNPay/MoMo thật mà không đụng tầng nghiệp vụ {@code [§13.2]}.
 */
public interface PaymentGateway {

    /**
     * Tạo yêu cầu thanh toán, trả về URL để chuyển hướng người dùng + mã giao dịch duy nhất.
     *
     * @param transactionCode mã giao dịch nội bộ (duy nhất — {@code [§3.14]})
     * @param amount          số tiền (VND)
     * @param description     mô tả đơn
     * @return thông tin để chuyển hướng thanh toán
     */
    PaymentInitResult createPayment(String transactionCode, BigDecimal amount, String description);

    /**
     * Xác thực callback/IPN từ cổng (chữ ký, chống replay) và trả về kết quả đã chuẩn hóa.
     *
     * @param rawParams tham số thô nhận từ cổng
     * @return kết quả callback đã xác thực
     */
    PaymentCallbackResult verifyCallback(java.util.Map<String, String> rawParams);

    /** Mã nhà cung cấp (SANDBOX/VNPAY/MOMO...) mà cài đặt này phục vụ. */
    String provider();
}
