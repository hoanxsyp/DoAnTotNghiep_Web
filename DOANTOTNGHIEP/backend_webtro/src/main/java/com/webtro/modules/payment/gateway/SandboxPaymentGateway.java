package com.webtro.modules.payment.gateway;

import com.webtro.config.properties.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Cổng thanh toán MÔ PHỎNG (canonical mục 3, §13.2). Không phụ thuộc dịch vụ ngoài: tạo URL trỏ về
 * một trang xác nhận nội bộ, và ký callback bằng HMAC-SHA256 với {@code PAYMENT_CALLBACK_SECRET}.
 *
 * <p>Đủ để thể hiện đầy đủ luồng nghiệp vụ (§8.2): tạo đơn → chuyển hướng → callback có chữ ký →
 * đối soát → kích hoạt gói. Khi triển khai thật chỉ cần thêm impl {@link PaymentGateway} khác
 * (VNPay/MoMo) mà không đụng {@code PaymentService}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SandboxPaymentGateway implements PaymentGateway {

    private static final String HMAC_ALGO = "HmacSHA256";
    private final AppProperties appProperties;

    @Override
    public PaymentInitResult createPayment(String transactionCode, BigDecimal amount, String description) {
        // Trang sandbox nội bộ ở frontend nhận mã giao dịch + chữ ký để người dùng bấm "Thanh toán".
        String signature = sign(transactionCode + "|" + amount.toPlainString());
        String url = appProperties.getPayment().getReturnUrl()
                + "?txn=" + transactionCode
                + "&amount=" + amount.toPlainString()
                + "&sig=" + signature;
        log.info("[SANDBOX] Tạo thanh toán {} số tiền {}", transactionCode, amount);
        return PaymentInitResult.builder()
                .paymentUrl(url)
                .transactionCode(transactionCode)
                .providerRef("SANDBOX-" + transactionCode)
                .build();
    }

    @Override
    public PaymentCallbackResult verifyCallback(Map<String, String> params) {
        String txn = params.getOrDefault("txn", "");
        String amountStr = params.getOrDefault("amount", "0");
        String sig = params.getOrDefault("sig", "");
        String status = params.getOrDefault("status", "success");

        String expected = sign(txn + "|" + amountStr);
        boolean valid = constantTimeEquals(expected, sig);

        return PaymentCallbackResult.builder()
                .signatureValid(valid)
                .transactionCode(txn)
                .success(valid && "success".equalsIgnoreCase(status))
                .amount(new BigDecimal(amountStr.isBlank() ? "0" : amountStr))
                .providerRef("SANDBOX-" + txn)
                .message(valid ? "OK" : "Chữ ký không hợp lệ")
                .build();
    }

    @Override
    public String provider() {
        return "SANDBOX";
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            String secret = appProperties.getPayment().getCallbackSecret();
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Không ký được callback thanh toán", e);
        }
    }

    /** So sánh thời gian hằng số để chống timing attack khi kiểm tra chữ ký. */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
