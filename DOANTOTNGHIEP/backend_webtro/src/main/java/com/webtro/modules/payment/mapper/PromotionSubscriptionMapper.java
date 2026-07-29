package com.webtro.modules.payment.mapper;

import com.webtro.modules.payment.entity.PromotionSubscription;
import com.webtro.modules.payment.dto.response.PromotionSubscriptionResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * Ánh xạ {@link PromotionSubscription} → DTO (thủ công, Builder). Các trường của tin/gói/giao dịch
 * ({@code listingTitle}, {@code packageName}, {@code transactionCode}, {@code amount}) do service
 * bổ sung vì nằm ở entity khác.
 */
@Component
public class PromotionSubscriptionMapper {

    public PromotionSubscriptionResponse toResponse(PromotionSubscription s, String transactionCode,
                                                    String listingTitle, String packageName,
                                                    String badgeLabel, BigDecimal amount) {
        return PromotionSubscriptionResponse.builder()
                .id(s.getId())
                .paymentId(s.getPaymentId())
                .transactionCode(transactionCode)
                .listingId(s.getListingId())
                .listingTitle(listingTitle)
                .packageId(s.getPackageId())
                .packageName(packageName)
                .badgeLabel(badgeLabel)
                .priority(s.getPriority())
                .amount(amount)
                .status(s.getStatus() == null ? null : s.getStatus().name())
                .statusLabel(s.getStatus() == null ? null : s.getStatus().getLabel())
                .startAt(s.getStartAt())
                .endAt(s.getEndAt())
                .daysRemaining(daysRemaining(s.getEndAt()))
                .build();
    }

    /** Khối rút gọn nhúng trong chi tiết giao dịch / callback (chỉ id + trạng thái + thời hạn). */
    public PromotionSubscriptionResponse toBrief(PromotionSubscription s) {
        return PromotionSubscriptionResponse.builder()
                .id(s.getId())
                .listingId(s.getListingId())
                .packageId(s.getPackageId())
                .priority(s.getPriority())
                .status(s.getStatus() == null ? null : s.getStatus().name())
                .statusLabel(s.getStatus() == null ? null : s.getStatus().getLabel())
                .startAt(s.getStartAt())
                .endAt(s.getEndAt())
                .daysRemaining(daysRemaining(s.getEndAt()))
                .build();
    }

    /** Số ngày còn lại làm tròn lên, tối thiểu 0. */
    private Long daysRemaining(Instant endAt) {
        if (endAt == null) {
            return null;
        }
        long seconds = Duration.between(Instant.now(), endAt).getSeconds();
        if (seconds <= 0) {
            return 0L;
        }
        return (seconds + 86_399L) / 86_400L;
    }
}
