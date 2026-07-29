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
 * Mã khuyến mãi trả ra API quản trị (canonical 4.18.9–4.18.11).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CouponResponse", description = "Mã khuyến mãi")
public class CouponResponse {

    @Schema(description = "Id coupon", example = "12")
    private Long id;

    @Schema(description = "Mã coupon", example = "HELLO2026")
    private String code;

    @Schema(description = "Mô tả")
    private String description;

    @Schema(description = "Loại giảm giá (CouponDiscountType)", example = "FIXED")
    private String discountType;

    @Schema(description = "Giá trị giảm", example = "20000.00")
    private BigDecimal discountValue;

    @Schema(description = "Trần giảm (chỉ áp cho PERCENT)")
    private BigDecimal maxDiscountAmount;

    @Schema(description = "Giá trị đơn tối thiểu", example = "50000.00")
    private BigDecimal minOrderAmount;

    @Schema(description = "Tổng lượt dùng tối đa (null = không giới hạn)", example = "500")
    private Integer usageLimit;

    @Schema(description = "Số lượt đã dùng", example = "0")
    private Integer usedCount;

    @Schema(description = "Số lượt tối đa mỗi người", example = "1")
    private Integer perUserLimit;

    @Schema(description = "Thời điểm bắt đầu hiệu lực (UTC)")
    private Instant startAt;

    @Schema(description = "Thời điểm kết thúc hiệu lực (UTC)")
    private Instant endAt;

    @Schema(description = "Còn hoạt động không", example = "true")
    private Boolean active;

    @Schema(description = "Thời điểm tạo (UTC)")
    private Instant createdAt;
}
