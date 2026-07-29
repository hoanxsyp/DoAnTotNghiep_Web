package com.webtro.modules.payment.dto.response;

import com.webtro.common.PageResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Lịch sử thanh toán kèm số liệu tổng hợp (canonical 4.9.6 "my", 4.18.5 admin).
 * Gói {@link PageResponse} các giao dịch cùng khối {@code summary}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "PaymentHistoryResponse", description = "Lịch sử thanh toán + tổng hợp")
public class PaymentHistoryResponse {

    @Schema(description = "Trang giao dịch")
    private PageResponse<PaymentResponse> page;

    @Schema(description = "Số liệu tổng hợp trong phạm vi lọc")
    private Summary summary;

    /** Số liệu tổng hợp theo trạng thái. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "PaymentSummary", description = "Tổng hợp giao dịch")
    public static class Summary {

        @Schema(description = "Tổng tiền các giao dịch thành công (VND)", example = "1245000.00")
        private BigDecimal totalPaid;

        @Schema(description = "Số giao dịch thành công", example = "6")
        private long successCount;

        @Schema(description = "Số giao dịch thất bại", example = "2")
        private long failedCount;

        @Schema(description = "Số giao dịch đang chờ", example = "0")
        private long pendingCount;

        @Schema(description = "Số giao dịch đã hoàn tiền", example = "1")
        private long refundedCount;

        @Schema(description = "Số giao dịch đã hủy", example = "0")
        private long cancelledCount;
    }
}
