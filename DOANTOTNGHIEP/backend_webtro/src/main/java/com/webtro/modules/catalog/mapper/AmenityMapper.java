package com.webtro.modules.catalog.mapper;

import com.webtro.common.enums.AmenityGroup;
import com.webtro.modules.catalog.dto.response.AmenityGroupResponse;
import com.webtro.modules.catalog.dto.response.AmenityResponse;
import com.webtro.modules.catalog.entity.Amenity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Chuyển {@link Amenity} ↔ DTO (thủ công, Builder — canonical mục 3).
 */
@Component
public class AmenityMapper {

    /** Entity → DTO. */
    public AmenityResponse toResponse(Amenity entity) {
        if (entity == null) {
            return null;
        }
        return AmenityResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .name(entity.getName())
                .group(entity.getGroupCode())
                .iconUrl(entity.getIcon())
                .filterable(entity.getIsFilterable())
                .priceImpactRatio(entity.getPriceImpactRatio())
                .displayOrder(entity.getDisplayOrder())
                .active(entity.getIsActive())
                .build();
    }

    /** Danh sách entity → danh sách DTO. */
    public List<AmenityResponse> toResponseList(List<Amenity> entities) {
        return entities.stream().map(this::toResponse).toList();
    }

    /**
     * Gom danh sách tiện ích theo {@link AmenityGroup}, giữ thứ tự 4 nhóm cố định
     * (nội thất → an ninh → sinh hoạt → giao thông) {@code [§10.5]}; nhóm rỗng vẫn xuất hiện để
     * frontend render đủ khung bộ lọc.
     */
    public List<AmenityGroupResponse> toGroupedResponse(List<Amenity> entities) {
        Map<AmenityGroup, List<AmenityResponse>> byGroup = entities.stream()
                .map(this::toResponse)
                .collect(Collectors.groupingBy(AmenityResponse::getGroup));
        return java.util.Arrays.stream(AmenityGroup.values())
                .map(group -> AmenityGroupResponse.builder()
                        .group(group)
                        .groupLabel(group.getLabel())
                        .items(byGroup.getOrDefault(group, List.of()))
                        .build())
                .toList();
    }
}
