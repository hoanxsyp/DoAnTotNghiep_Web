package com.webtro.modules.payment.mapper;

import com.webtro.modules.payment.dto.response.PromotionPackageResponse;
import com.webtro.modules.payment.entity.PromotionPackage;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Ánh xạ {@link PromotionPackage} → DTO (thủ công, Builder). {@code highlighted} suy ra từ việc gói
 * có nhãn huy hiệu hay không (entity không có cột riêng). Các số liệu thống kê quản trị
 * ({@code purchaseCount}, {@code activeSubscriptionCount}, {@code totalRevenue}) do service set thêm.
 */
@Component
public class PromotionPackageMapper {

    /** Bản công khai: không kèm số liệu quản trị. */
    public PromotionPackageResponse toPublic(PromotionPackage p) {
        return base(p).build();
    }

    /** Bản quản trị: kèm số lượt mua + số gói đang chạy + doanh thu + thời điểm tạo. */
    public PromotionPackageResponse toAdmin(PromotionPackage p, long activeSubscriptionCount,
                                            BigDecimal totalRevenue) {
        return base(p)
                .activeSubscriptionCount(activeSubscriptionCount)
                .totalRevenue(totalRevenue == null ? BigDecimal.ZERO : totalRevenue)
                .createdAt(p.getCreatedAt())
                .build();
    }

    private PromotionPackageResponse.PromotionPackageResponseBuilder base(PromotionPackage p) {
        return PromotionPackageResponse.builder()
                .id(p.getId())
                .code(p.getCode())
                .name(p.getName())
                .description(p.getDescription())
                .price(p.getPrice())
                .durationDays(p.getDurationDays())
                .priority(p.getPriority())
                .badgeLabel(p.getBadgeLabel())
                .badgeColor(p.getBadgeColor())
                .highlighted(p.getBadgeLabel() != null && !p.getBadgeLabel().isBlank())
                .active(p.getIsActive())
                .displayOrder(p.getDisplayOrder())
                .purchaseCount(p.getPurchaseCount());
    }
}
