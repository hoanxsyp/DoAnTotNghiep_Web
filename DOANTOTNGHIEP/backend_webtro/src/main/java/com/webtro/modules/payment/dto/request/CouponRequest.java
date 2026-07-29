package com.webtro.modules.payment.dto.request;

import com.webtro.common.enums.CouponDiscountType;
import com.webtro.validator.NoHtml;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Yêu cầu tạo/sửa mã khuyến mãi (canonical 4.18.10, 4.18.11).
 * Ràng buộc phụ thuộc field (PERCENT cần {@code maxDiscountAmount}, {@code endAt > startAt}) kiểm ở service.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CouponRequest", description = "Yêu cầu tạo/sửa mã khuyến mãi")
public class CouponRequest {

    @Schema(description = "Mã coupon (duy nhất, bất biến khi sửa)", example = "HELLO2026")
    @NotBlank(message = "Vui lòng nhập mã coupon")
    @Size(min = 4, max = 32, message = "Mã coupon phải từ 4 đến 32 ký tự")
    @Pattern(regexp = "^[A-Z0-9_-]+$", message = "Mã coupon chỉ gồm chữ HOA, số, gạch dưới và gạch ngang")
    private String code;

    @Schema(description = "Mô tả hiển thị", example = "Giảm 20.000 đ cho gói đẩy tin đầu tiên")
    @NotBlank(message = "Vui lòng nhập mô tả")
    @Size(min = 5, max = 255, message = "Mô tả phải từ 5 đến 255 ký tự")
    @NoHtml
    private String description;

    @Schema(description = "Loại giảm giá (PERCENT | FIXED)", example = "FIXED")
    @NotNull(message = "Vui lòng chọn loại giảm giá")
    private CouponDiscountType discountType;

    @Schema(description = "Giá trị giảm (PERCENT: 1..100; FIXED: số tiền VND)", example = "20000.00")
    @NotNull(message = "Vui lòng nhập giá trị giảm")
    @DecimalMin(value = "0.0", inclusive = false, message = "Giá trị giảm phải lớn hơn 0")
    private BigDecimal discountValue;

    @Schema(description = "Trần giảm (bắt buộc khi PERCENT)", example = "50000.00")
    @DecimalMin(value = "0.0", inclusive = false, message = "Trần giảm phải lớn hơn 0")
    private BigDecimal maxDiscountAmount;

    @Schema(description = "Giá trị đơn tối thiểu (mặc định 0)", example = "50000.00")
    @DecimalMin(value = "0.0", message = "Giá trị đơn tối thiểu không được âm")
    private BigDecimal minOrderAmount;

    @Schema(description = "Thời điểm bắt đầu hiệu lực (UTC)")
    @NotNull(message = "Vui lòng nhập thời điểm bắt đầu")
    private Instant startAt;

    @Schema(description = "Thời điểm kết thúc hiệu lực (UTC)")
    @NotNull(message = "Vui lòng nhập thời điểm kết thúc")
    private Instant endAt;

    @Schema(description = "Tổng lượt dùng tối đa (null = không giới hạn)", example = "500")
    @Min(value = 1, message = "Tổng lượt dùng phải từ 1 trở lên")
    private Integer usageLimit;

    @Schema(description = "Số lượt tối đa mỗi người (mặc định 1)", example = "1")
    @Min(value = 1, message = "Số lượt mỗi người phải từ 1 trở lên")
    private Integer perUserLimit;

    @Schema(description = "Coupon còn hoạt động không (mặc định true)", example = "true")
    private Boolean active;
}
