package com.webtro.modules.payment.dto.request;

import com.webtro.validator.NoHtml;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Yêu cầu đánh dấu hoàn tiền thủ công — {@code PUT /api/admin/payments/{id}/refund} (canonical 4.18.7).
 * Chỉ đổi trạng thái trong hệ thống (SUCCESS → REFUNDED), KHÔNG gọi API hoàn tiền thật của cổng.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "RefundPaymentRequest", description = "Yêu cầu đánh dấu hoàn tiền")
public class RefundPaymentRequest {

    @Schema(description = "Lý do hoàn tiền", example = "Tin bị khóa sau khi kích hoạt gói; hoàn tiền theo chính sách")
    @NotBlank(message = "Vui lòng nhập lý do hoàn tiền")
    @Size(min = 10, max = 500, message = "Lý do phải từ 10 đến 500 ký tự")
    @NoHtml
    private String reason;

    @Schema(description = "Số tiền hoàn (mặc định = số tiền đã trả)", example = "299000.00")
    @DecimalMin(value = "0.0", inclusive = false, message = "Số tiền hoàn phải lớn hơn 0")
    private BigDecimal refundAmount;

    @Schema(description = "Hủy luôn gói đang chạy không (mặc định true)", example = "true")
    private Boolean cancelSubscription;
}
