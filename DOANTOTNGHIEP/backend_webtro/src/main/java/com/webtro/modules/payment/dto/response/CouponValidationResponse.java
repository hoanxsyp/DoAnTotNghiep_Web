package com.webtro.modules.payment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Kết quả kiểm tra mã khuyến mãi cho một gói cụ thể (canonical 4.9.9).
 * Chỉ tính toán, KHÔNG tiêu mã ({@code used_count} chỉ tăng khi callback SUCCESS).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CouponValidationResponse", description = "Kết quả áp mã khuyến mãi")
public class CouponValidationResponse {

    @Schema(description = "Mã coupon", example = "HELLO2026")
    private String code;

    @Schema(description = "Hợp lệ hay không", example = "true")
    private boolean valid;

    @Schema(description = "Mô tả coupon")
    private String description;

    @Schema(description = "Loại giảm giá (CouponDiscountType)", example = "FIXED")
    private String discountType;

    @Schema(description = "Giá trị giảm cấu hình", example = "20000.00")
    private BigDecimal discountValue;

    @Schema(description = "Số tiền gốc của gói (VND)", example = "99000.00")
    private BigDecimal originalAmount;

    @Schema(description = "Số tiền được giảm thực tế (VND)", example = "20000.00")
    private BigDecimal discountAmount;

    @Schema(description = "Số tiền phải trả sau giảm (VND, sàn 0)", example = "79000.00")
    private BigDecimal finalAmount;

    @Schema(description = "Thời điểm hết hiệu lực coupon (UTC)")
    private Instant validTo;
}
