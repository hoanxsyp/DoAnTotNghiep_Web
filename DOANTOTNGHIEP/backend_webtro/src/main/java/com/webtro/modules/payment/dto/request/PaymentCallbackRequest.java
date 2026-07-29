package com.webtro.modules.payment.dto.request;

import com.webtro.common.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Callback/IPN từ cổng thanh toán — {@code POST /api/payments/callback} (canonical 6.2).
 * Công khai (không JWT) nhưng bảo vệ bằng HMAC signature; xác thực chữ ký + chống replay ở service.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "PaymentCallbackRequest", description = "Callback từ cổng thanh toán")
public class PaymentCallbackRequest {

    @Schema(description = "Mã giao dịch nội bộ của hệ thống", example = "WT20260717K3M9QA7Z")
    @NotBlank(message = "Thiếu mã giao dịch")
    @Size(min = 10, max = 32, message = "Mã giao dịch không hợp lệ")
    private String transactionCode;

    @Schema(description = "Mã giao dịch phía cổng", example = "VNP-1784282520-7001")
    @NotBlank(message = "Thiếu mã giao dịch phía cổng")
    @Size(max = 100, message = "Mã giao dịch phía cổng quá dài")
    private String gatewayTransactionId;

    @Schema(description = "Số tiền cổng xác nhận (VND)", example = "79000.00")
    @NotNull(message = "Thiếu số tiền")
    @DecimalMin(value = "0.0", inclusive = false, message = "Số tiền phải lớn hơn 0")
    private BigDecimal amount;

    @Schema(description = "Kết quả (SUCCESS | FAILED | CANCELLED)", example = "SUCCESS")
    @NotNull(message = "Thiếu trạng thái")
    private PaymentStatus status;

    @Schema(description = "Mã trả về của cổng (00 = thành công)", example = "00")
    @NotBlank(message = "Thiếu mã trả về")
    @Size(max = 20, message = "Mã trả về quá dài")
    private String responseCode;

    @Schema(description = "Mô tả kết quả từ cổng", example = "Giao dich thanh cong")
    @Size(max = 255, message = "Mô tả quá dài")
    private String responseMessage;

    @Schema(description = "Thời điểm thanh toán (bắt buộc khi SUCCESS)")
    private Instant paidAt;

    @Schema(description = "Epoch second, chống replay", example = "1784282520")
    @NotNull(message = "Thiếu timestamp")
    private Long timestamp;

    @Schema(description = "UUID v4 chống replay", example = "b7e2c1a4-9f38-4d05-8e61-3c2d7a9b0f14")
    @NotBlank(message = "Thiếu nonce")
    private String nonce;

    @Schema(description = "Chữ ký HMAC-SHA256 (64 ký tự hex)")
    @NotBlank(message = "Thiếu chữ ký")
    private String signature;
}
