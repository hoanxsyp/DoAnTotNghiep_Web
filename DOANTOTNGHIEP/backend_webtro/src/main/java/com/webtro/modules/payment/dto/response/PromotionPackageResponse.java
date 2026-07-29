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
 * Thông tin gói dịch vụ đẩy tin trả ra API (canonical 4.9.1, 4.18.1).
 *
 * <p>Các field {@code purchaseCount}/{@code activeSubscriptionCount}/{@code totalRevenue}/
 * {@code createdAt} chỉ được set ở luồng quản trị ({@code PACKAGE_MANAGE}); luồng công khai để null
 * và bị loại khỏi JSON.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "PromotionPackageResponse", description = "Gói dịch vụ đẩy tin")
public class PromotionPackageResponse {

    @Schema(description = "Id gói", example = "1")
    private Long id;

    @Schema(description = "Mã gói (duy nhất)", example = "PUSH_TOP_7D")
    private String code;

    @Schema(description = "Tên hiển thị", example = "Đẩy tin lên đầu 7 ngày")
    private String name;

    @Schema(description = "Mô tả gói")
    private String description;

    @Schema(description = "Giá gói (VND)", example = "99000.00")
    private BigDecimal price;

    @Schema(description = "Số ngày hiệu lực", example = "7")
    private Integer durationDays;

    @Schema(description = "Mức ưu tiên hiển thị khi đẩy (0..promotion.max_priority)", example = "80")
    private Integer priority;

    @Schema(description = "Nhãn huy hiệu hiển thị trên tin", example = "Tin nổi bật")
    private String badgeLabel;

    @Schema(description = "Màu huy hiệu", example = "#FF5722")
    private String badgeColor;

    @Schema(description = "Có làm nổi bật trên trang bán gói không", example = "true")
    private Boolean highlighted;

    @Schema(description = "Gói còn được bán không", example = "true")
    private Boolean active;

    @Schema(description = "Thứ tự hiển thị", example = "1")
    private Integer displayOrder;

    @Schema(description = "Số lượt đã mua (chỉ luồng quản trị)", example = "342")
    private Integer purchaseCount;

    @Schema(description = "Số gói đang hiệu lực (chỉ luồng quản trị)", example = "8")
    private Long activeSubscriptionCount;

    @Schema(description = "Tổng doanh thu gói (chỉ luồng quản trị)", example = "33858000.00")
    private BigDecimal totalRevenue;

    @Schema(description = "Thời điểm tạo (chỉ luồng quản trị)")
    private Instant createdAt;
}
