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
 * Giao dịch thanh toán trả ra API. Dùng chung cho:
 * <ul>
 *   <li>tạo đơn (canonical 4.9.3) — có {@code paymentUrl};</li>
 *   <li>chi tiết giao dịch (4.9.5) — có {@code subscription}, {@code refundable};</li>
 *   <li>phần tử trong lịch sử của tôi / danh sách admin (4.9.6, 4.18.5).</li>
 * </ul>
 * Field null bị loại khỏi JSON nên mỗi ngữ cảnh chỉ mang field liên quan.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "PaymentResponse", description = "Giao dịch thanh toán")
public class PaymentResponse {

    @Schema(description = "Id giao dịch", example = "7001")
    private Long id;

    @Schema(description = "Mã giao dịch nội bộ (duy nhất)", example = "WT20260717K3M9QA7Z")
    private String transactionCode;

    @Schema(description = "Mã giao dịch phía cổng (chỉ hiện với PAYMENT_MANAGE)",
            example = "SANDBOX-WT20260717K3M9QA7Z")
    private String gatewayTransactionId;

    @Schema(description = "Người thanh toán (users.id)", example = "42")
    private Long userId;

    @Schema(description = "Id tin được đẩy", example = "1024")
    private Long listingId;

    @Schema(description = "Tiêu đề tin", example = "Phòng trọ mới xây, có gác lửng, Q. Bình Thạnh")
    private String listingTitle;

    @Schema(description = "Id gói", example = "1")
    private Long packageId;

    @Schema(description = "Tên gói", example = "Đẩy tin lên đầu 7 ngày")
    private String packageName;

    @Schema(description = "Số tiền gốc trước giảm (VND)", example = "99000.00")
    private BigDecimal originalAmount;

    @Schema(description = "Số tiền được giảm (VND)", example = "20000.00")
    private BigDecimal discountAmount;

    @Schema(description = "Số tiền thực trả (VND)", example = "79000.00")
    private BigDecimal amount;

    @Schema(description = "Mã coupon đã áp (nếu có)", example = "HELLO2026")
    private String couponCode;

    @Schema(description = "Phương thức thanh toán (PaymentMethod)", example = "SANDBOX")
    private String paymentMethod;

    @Schema(description = "Trạng thái (PaymentStatus)", example = "PENDING")
    private String status;

    @Schema(description = "Nhãn trạng thái tiếng Việt", example = "Chờ thanh toán")
    private String statusLabel;

    @Schema(description = "Lý do thất bại (khi FAILED)")
    private String failureReason;

    @Schema(description = "URL chuyển hướng thanh toán (chỉ có ở response tạo đơn)")
    private String paymentUrl;

    @Schema(description = "Lượt đẩy đã kích hoạt (chỉ ở chi tiết khi đã SUCCESS)")
    private PromotionSubscriptionResponse subscription;

    @Schema(description = "Có thể hoàn tiền không (SUCCESS và chưa hoàn) — chỉ Admin dùng", example = "true")
    private Boolean refundable;

    @Schema(description = "Số tiền đã hoàn (khi REFUNDED)")
    private BigDecimal refundAmount;

    @Schema(description = "Thời điểm tạo đơn (UTC)")
    private Instant createdAt;

    @Schema(description = "Thời điểm thanh toán thành công (UTC)")
    private Instant paidAt;

    @Schema(description = "Thời điểm hết hạn đơn (UTC)")
    private Instant expiresAt;
}
