package com.webtro.modules.payment.entity;

import com.webtro.common.AuditableEntity;
import com.webtro.common.enums.SubscriptionStatus;
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

import java.time.Instant;

/**
 * Lượt đẩy tin đã kích hoạt ({@code promotion_subscriptions}) — mục 36 của V1 baseline.
 *
 * <p>Bảng nghiệp vụ (có xóa mềm) nên kế thừa {@link AuditableEntity}. Mỗi bản ghi tương ứng một
 * giao dịch thành công ({@code payment_id} duy nhất) áp gói đẩy cho một tin đăng trong khoảng
 * [{@code start_at}, {@code end_at}].
 *
 * <p>Các khóa {@code listing_id}, {@code user_id} trỏ sang module khác nên chỉ giữ dạng {@code Long}
 * theo luật ranh giới module (canonical luật 8). Khóa {@code payment_id}, {@code package_id} trỏ tới
 * bảng cùng module nhưng vẫn giữ {@code Long} cho nhất quán và an toàn với {@code ddl-auto=validate}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(
        name = "promotion_subscriptions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_promotion_subscriptions_payment_id", columnNames = "payment_id")
        },
        indexes = {
                @Index(name = "idx_promotion_subscriptions_status_end_at", columnList = "status, end_at"),
                @Index(name = "idx_promotion_subscriptions_listing_id_status", columnList = "listing_id, status"),
                @Index(name = "idx_promotion_subscriptions_user_id", columnList = "user_id"),
                @Index(name = "idx_promotion_subscriptions_package_id", columnList = "package_id")
        }
)
public class PromotionSubscription extends AuditableEntity {

    /** Giao dịch thanh toán tương ứng (payments.id), duy nhất. */
    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    /** Tin đăng được đẩy (listings.id). Khóa liên module -> giữ Long. */
    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    /** Gói đẩy tin (promotion_packages.id). */
    @Column(name = "package_id", nullable = false)
    private Long packageId;

    /** Chủ sở hữu lượt đẩy (users.id). Khóa liên module -> giữ Long. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Mức ưu tiên hiển thị (0..100, sao chép từ gói lúc kích hoạt). */
    @Builder.Default
    @Column(name = "priority", nullable = false)
    private Integer priority = 0;

    /** Trạng thái lượt đẩy, lưu VARCHAR. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private SubscriptionStatus status;

    /** Thời điểm bắt đầu đẩy (UTC). */
    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    /** Thời điểm kết thúc đẩy (UTC). */
    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    /** Lý do hủy (khi CANCELLED). */
    @Column(name = "cancelled_reason", length = 500)
    private String cancelledReason;
}
