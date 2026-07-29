package com.webtro.modules.payment.dto.request;

import com.webtro.common.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu tạo giao dịch mua gói đẩy tin — {@code POST /api/payments} (canonical 4.9.3).
 * Điều kiện tin (chủ sở hữu, trạng thái) và coupon được kiểm ở service.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CreatePaymentRequest", description = "Yêu cầu tạo giao dịch mua gói đẩy tin")
public class CreatePaymentRequest {

    @Schema(description = "Id tin cần đẩy", example = "1024")
    @NotNull(message = "Vui lòng chọn tin cần đẩy")
    private Long listingId;

    @Schema(description = "Id gói dịch vụ", example = "1")
    @NotNull(message = "Vui lòng chọn gói dịch vụ")
    private Long packageId;

    @Schema(description = "Phương thức thanh toán", example = "SANDBOX")
    @NotNull(message = "Vui lòng chọn phương thức thanh toán")
    private PaymentMethod paymentMethod;

    @Schema(description = "Mã khuyến mãi (tùy chọn)", example = "HELLO2026")
    @Size(min = 4, max = 32, message = "Mã khuyến mãi phải từ 4 đến 32 ký tự")
    @Pattern(regexp = "^[A-Z0-9_-]+$", message = "Mã khuyến mãi chỉ gồm chữ HOA, số, gạch dưới và gạch ngang")
    private String couponCode;

    @Schema(description = "URL trang kết quả ở frontend", example = "http://localhost:5173/quan-ly/thanh-toan/ket-qua")
    @NotBlank(message = "Thiếu URL nhận kết quả thanh toán")
    private String returnUrl;
}
