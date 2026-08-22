package com.webtro.modules.admin.mapper;

import com.webtro.modules.admin.dto.response.AdminListingResponse;
import com.webtro.modules.catalog.entity.District;
import com.webtro.modules.catalog.entity.Province;
import com.webtro.modules.catalog.entity.Ward;
import com.webtro.modules.catalog.repository.WardRepository;
import com.webtro.modules.listing.entity.Listing;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Mapper thủ công {@link Listing} → {@link AdminListingResponse} (Builder, không MapStruct).
 * {@code reportCount} tính riêng ở service (đếm từ module moderation) rồi truyền vào.
 */
@Component
@RequiredArgsConstructor
public class AdminListingMapper {

    private final WardRepository wardRepository;

    public AdminListingResponse toResponse(Listing l, Long reportCount) {
        LocationParts location = locationOf(l.getWardId());
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
                .provinceId(location.province() == null ? null : location.province().getId())
                .districtId(location.district() == null ? null : location.district().getId())
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

    private LocationParts locationOf(Long wardId) {
        Ward ward = wardId == null ? null : wardRepository.findById(wardId).orElse(null);
        District district = ward == null ? null : ward.getDistrict();
        Province province = district == null ? null : district.getProvince();
        return new LocationParts(province, district);
    }

    private record LocationParts(Province province, District district) {
    }
}
