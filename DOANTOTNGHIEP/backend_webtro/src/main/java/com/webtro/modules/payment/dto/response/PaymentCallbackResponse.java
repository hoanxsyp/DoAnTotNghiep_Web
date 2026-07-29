package com.webtro.modules.payment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Kết quả xử lý callback thanh toán trả về cho cổng (canonical 6.7).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "PaymentCallbackResponse", description = "Kết quả xử lý callback")
public class PaymentCallbackResponse {

    @Schema(description = "Mã giao dịch nội bộ", example = "WT20260717K3M9QA7Z")
    private String transactionCode;

    @Schema(description = "Id giao dịch", example = "7001")
    private Long paymentId;

    @Schema(description = "Trạng thái sau xử lý (PaymentStatus)", example = "SUCCESS")
    private String status;

    @Schema(description = "Trạng thái trước xử lý", example = "PENDING")
    private String previousStatus;

    @Schema(description = "Callback đã được xử lý trước đó (idempotent)", example = "false")
    private boolean alreadyProcessed;

    @Schema(description = "Lượt đẩy đã kích hoạt (khi SUCCESS)")
    private PromotionSubscriptionResponse subscription;

    @Schema(description = "Mã coupon vừa được tiêu (nếu có)")
    private String couponConsumed;

    @Schema(description = "Đã gửi thông báo cho người dùng chưa", example = "true")
    private boolean userNotified;

    @Schema(description = "Thời điểm xử lý (UTC)")
    private Instant processedAt;
}
