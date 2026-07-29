package com.webtro.modules.payment.entity;

import com.webtro.common.AuditableEntity;
import com.webtro.common.enums.PaymentMethod;
import com.webtro.common.enums.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Giao dịch thanh toán ({@code payments}) — mục 35 của V1 baseline.
 *
 * <p>Bảng nghiệp vụ (có xóa mềm) nên kế thừa {@link AuditableEntity}. Một giao dịch mua gói đẩy
 * tin cho một tin đăng, có thể áp mã giảm giá. Ràng buộc DB: {@code final_amount = amount -
 * discount_amount} và phải có ít nhất {@code listing_id} hoặc {@code package_id}.
 *
 * <p>Các khóa {@code user_id}, {@code listing_id}, {@code refunded_by} trỏ sang module khác nên chỉ
 * giữ dạng {@code Long} theo luật ranh giới module (canonical luật 8). Khóa {@code package_id},
 * {@code coupon_id} trỏ tới bảng cùng module nhưng vẫn giữ {@code Long} cho nhất quán, đơn giản và
 * an toàn với {@code ddl-auto=validate}; tầng service tự nạp entity liên quan khi cần.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(
        name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payments_transaction_code", columnNames = "transaction_code")
        },
        indexes = {
                @Index(name = "idx_payments_user_id_created_at", columnList = "user_id, created_at"),
                @Index(name = "idx_payments_status_created_at", columnList = "status, created_at"),
                @Index(name = "idx_payments_status_expires_at", columnList = "status, expires_at"),
                @Index(name = "idx_payments_paid_at", columnList = "paid_at"),
                @Index(name = "idx_payments_gateway_txn_ref", columnList = "gateway_txn_ref"),
                @Index(name = "idx_payments_listing_id", columnList = "listing_id"),
                @Index(name = "idx_payments_package_id", columnList = "package_id"),
                @Index(name = "idx_payments_coupon_id", columnList = "coupon_id")
        }
)
public class Payment extends AuditableEntity {

    /** Người thanh toán (users.id). Khóa liên module -> giữ Long. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Tin đăng được đẩy (listings.id), có thể null. Khóa liên module -> giữ Long. */
    @Column(name = "listing_id")
    private Long listingId;

    /** Gói đẩy tin (promotion_packages.id), có thể null. */
    @Column(name = "package_id")
    private Long packageId;

    /** Mã giảm giá đã áp (coupons.id), có thể null. */
    @Column(name = "coupon_id")
    private Long couponId;

    /** Số tiền gốc trước giảm. */
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /** Số tiền được giảm. */
    @Builder.Default
    @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    /** Số tiền thực trả (= amount - discount_amount). */
    @Column(name = "final_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal finalAmount;

    /** Đơn vị tiền tệ (CHAR(3)). */
    @Builder.Default
    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "char(3)")
    private String currency = "VND";

    /** Phương thức thanh toán, lưu VARCHAR. */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    /** Mã giao dịch nội bộ (duy nhất). */
    @Column(name = "transaction_code", nullable = false, length = 50)
    private String transactionCode;

    /** Mã tham chiếu giao dịch phía cổng thanh toán. */
    @Column(name = "gateway_txn_ref", length = 100)
    private String gatewayTxnRef;

    /** Phản hồi thô từ cổng thanh toán, lưu JSON. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gateway_response", columnDefinition = "json")
    private String gatewayResponse;

    /** Trạng thái giao dịch, lưu VARCHAR. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private PaymentStatus status;

    /** Lý do thất bại (khi FAILED). */
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    /** Thời điểm thanh toán thành công (UTC). */
    @Column(name = "paid_at")
    private Instant paidAt;

    /** Thời điểm hết hạn thanh toán (UTC). */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Thời điểm hoàn tiền (UTC). */
    @Column(name = "refunded_at")
    private Instant refundedAt;

    /** Số tiền đã hoàn. */
    @Column(name = "refund_amount", precision = 15, scale = 2)
    private BigDecimal refundAmount;

    /** Ghi chú hoàn tiền. */
    @Column(name = "refund_note", length = 500)
    private String refundNote;

    /** Người thực hiện hoàn tiền (users.id). Khóa liên module -> giữ Long. */
    @Column(name = "refunded_by")
    private Long refundedBy;

    /** IP của client khi tạo giao dịch. */
    @Column(name = "client_ip", length = 45)
    private String clientIp;
}
