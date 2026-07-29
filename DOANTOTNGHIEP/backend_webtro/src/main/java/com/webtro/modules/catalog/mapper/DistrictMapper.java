package com.webtro.modules.catalog.mapper;

import com.webtro.modules.catalog.dto.response.DistrictResponse;
import com.webtro.modules.catalog.entity.District;
import org.springframework.stereotype.Component;

/**
 * Chuyển {@link District} ↔ DTO (thủ công, Builder — canonical mục 3).
 */
@Component
public class DistrictMapper {

    /** Entity → DTO. */
    public DistrictResponse toResponse(District entity) {
        if (entity == null) {
            return null;
        }
        return DistrictResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .provinceId(entity.getProvince() != null ? entity.getProvince().getId() : null)
                .name(entity.getName())
                .slug(entity.getSlug())
                .type(entity.getType())
                .supported(entity.getIsActive())
                .listingCount(entity.getListingCount())
                .build();
    }
}
