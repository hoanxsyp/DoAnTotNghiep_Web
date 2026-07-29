package com.webtro.modules.payment.dto.request;

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
 * Yêu cầu kiểm tra mã khuyến mãi cho một gói — {@code POST /api/coupons/validate} (canonical 4.9.9).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ValidateCouponRequest", description = "Yêu cầu kiểm tra mã khuyến mãi")
public class ValidateCouponRequest {

    @Schema(description = "Mã khuyến mãi", example = "HELLO2026")
    @NotBlank(message = "Vui lòng nhập mã khuyến mãi")
    @Size(min = 4, max = 32, message = "Mã khuyến mãi phải từ 4 đến 32 ký tự")
    @Pattern(regexp = "^[A-Z0-9_-]+$", message = "Mã khuyến mãi chỉ gồm chữ HOA, số, gạch dưới và gạch ngang")
    private String code;

    @Schema(description = "Id gói định mua", example = "1")
    @NotNull(message = "Vui lòng chọn gói dịch vụ")
    private Long packageId;
}
