package com.webtro.modules.payment.mapper;

import com.webtro.modules.payment.dto.response.CouponResponse;
import com.webtro.modules.payment.entity.Coupon;
import org.springframework.stereotype.Component;

/**
 * Ánh xạ {@link Coupon} → DTO (thủ công, Builder).
 */
@Component
public class CouponMapper {

    public CouponResponse toResponse(Coupon c) {
        return CouponResponse.builder()
                .id(c.getId())
                .code(c.getCode())
                .description(c.getDescription())
                .discountType(c.getDiscountType() == null ? null : c.getDiscountType().name())
                .discountValue(c.getDiscountValue())
                .maxDiscountAmount(c.getMaxDiscountAmount())
                .minOrderAmount(c.getMinOrderAmount())
                .usageLimit(c.getUsageLimit())
                .usedCount(c.getUsedCount())
                .perUserLimit(c.getPerUserLimit())
                .startAt(c.getStartAt())
                .endAt(c.getEndAt())
                .active(c.getIsActive())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
