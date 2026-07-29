package com.webtro.modules.admin.mapper;

import com.webtro.modules.admin.dto.response.AdminListingResponse;
import com.webtro.modules.listing.entity.Listing;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Mapper thủ công {@link Listing} → {@link AdminListingResponse} (Builder, không MapStruct).
 * {@code reportCount} tính riêng ở service (đếm từ module moderation) rồi truyền vào.
 */
@Component
public class AdminListingMapper {

    public AdminListingResponse toResponse(Listing l, Long reportCount) {
        return AdminListingResponse.builder()
                .id(l.getId())
                .title(l.getTitle())
                .slug(l.getSlug())
                .categoryId(l.getCategoryId())
                .price(l.getPrice())
                .area(l.getArea())
                .ownerId(l.getOwnerId())
                .status(l.getStatus() != null ? l.getStatus().name() : null)
                .statusLabel(l.getStatus() != null ? l.getStatus().getLabel() : null)
                .trustScore(toInt(l.getTrustScore()))
                .reportCount(reportCount)
                .priceDeviationFlagged(l.getPriceDeviationFlag())
                .provinceId(l.getProvinceId())
                .districtId(l.getDistrictId())
                .wardId(l.getWardId())
                .publishedAt(l.getPublishedAt())
                .expiredAt(l.getExpiredAt())
                .createdAt(l.getCreatedAt())
                .updatedAt(l.getUpdatedAt())
                .build();
    }

    private Integer toInt(BigDecimal v) {
        return v != null ? v.intValue() : null;
    }
}
