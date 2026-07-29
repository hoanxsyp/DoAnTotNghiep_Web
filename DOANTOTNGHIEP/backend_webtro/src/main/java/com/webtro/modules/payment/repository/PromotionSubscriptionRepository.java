package com.webtro.modules.payment.repository;

import com.webtro.common.enums.SubscriptionStatus;
import com.webtro.modules.payment.entity.PromotionSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Truy vấn lượt đẩy tin đã kích hoạt. Có {@link JpaSpecificationExecutor} phục vụ lọc động.
 * Mọi truy vấn công khai kèm điều kiện {@code deleted_at IS NULL} (xóa mềm).
 */
@Repository
public interface PromotionSubscriptionRepository
        extends JpaRepository<PromotionSubscription, Long>, JpaSpecificationExecutor<PromotionSubscription> {

    /** Lấy một lượt đẩy chưa xóa mềm theo id. */
    Optional<PromotionSubscription> findByIdAndDeletedAtIsNull(Long id);

    /** Lấy lượt đẩy theo giao dịch (payment_id là duy nhất). */
    Optional<PromotionSubscription> findByPaymentIdAndDeletedAtIsNull(Long paymentId);

    /** Các lượt đẩy của một tin theo trạng thái (vd đang ACTIVE). */
    List<PromotionSubscription> findByListingIdAndStatusAndDeletedAtIsNull(
            Long listingId, SubscriptionStatus status);

    /** Các lượt đẩy đã hết hạn cần chuyển trạng thái (job nền quét EXPIRED). */
    List<PromotionSubscription> findByStatusAndEndAtBeforeAndDeletedAtIsNull(
            SubscriptionStatus status, Instant threshold);
}
