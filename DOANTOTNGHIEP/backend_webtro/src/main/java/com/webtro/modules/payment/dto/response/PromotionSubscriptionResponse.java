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
 * Lượt đẩy tin đã kích hoạt trả ra API (canonical 4.9.8) và khối {@code subscription} nhúng trong
 * chi tiết giao dịch / callback.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "PromotionSubscriptionResponse", description = "Lượt đẩy tin đã kích hoạt")
public class PromotionSubscriptionResponse {

    @Schema(description = "Id lượt đẩy", example = "3301")
    private Long id;

    @Schema(description = "Id giao dịch tương ứng", example = "7001")
    private Long paymentId;

    @Schema(description = "Mã giao dịch nội bộ", example = "WT20260717K3M9QA7Z")
    private String transactionCode;

    @Schema(description = "Id tin được đẩy", example = "1024")
    private Long listingId;

    @Schema(description = "Tiêu đề tin", example = "Phòng trọ mới xây, có gác lửng, Q. Bình Thạnh")
    private String listingTitle;

    @Schema(description = "Id gói", example = "1")
    private Long packageId;

    @Schema(description = "Tên gói", example = "Đẩy tin lên đầu 7 ngày")
    private String packageName;

    @Schema(description = "Nhãn huy hiệu", example = "Tin nổi bật")
    private String badgeLabel;

    @Schema(description = "Mức ưu tiên", example = "80")
    private Integer priority;

    @Schema(description = "Số tiền đã trả cho lượt đẩy", example = "79000.00")
    private BigDecimal amount;

    @Schema(description = "Trạng thái lượt đẩy (SubscriptionStatus)", example = "ACTIVE")
    private String status;

    @Schema(description = "Nhãn trạng thái tiếng Việt", example = "Đang chạy")
    private String statusLabel;

    @Schema(description = "Thời điểm bắt đầu (UTC)")
    private Instant startAt;

    @Schema(description = "Thời điểm kết thúc (UTC)")
    private Instant endAt;

    @Schema(description = "Số ngày còn lại (làm tròn lên; 0 nếu đã hết)", example = "7")
    private Long daysRemaining;
}
