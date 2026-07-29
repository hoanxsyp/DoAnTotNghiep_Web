package com.webtro.modules.catalog.mapper;

import com.webtro.modules.catalog.dto.response.WardResponse;
import com.webtro.modules.catalog.entity.Ward;
import org.springframework.stereotype.Component;

/**
 * Chuyển {@link Ward} ↔ DTO (thủ công, Builder — canonical mục 3).
 */
@Component
public class WardMapper {

    /** Entity → DTO. */
    public WardResponse toResponse(Ward entity) {
        if (entity == null) {
            return null;
        }
        return WardResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .districtId(entity.getDistrict() != null ? entity.getDistrict().getId() : null)
                .name(entity.getName())
                .slug(entity.getSlug())
                .type(entity.getType())
                .supported(entity.getIsActive())
                .listingCount(entity.getListingCount())
                .build();
    }
}
