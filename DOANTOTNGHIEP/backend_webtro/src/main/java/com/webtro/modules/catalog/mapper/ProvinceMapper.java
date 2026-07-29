package com.webtro.modules.catalog.mapper;

import com.webtro.modules.catalog.dto.response.AdminProvinceResponse;
import com.webtro.modules.catalog.dto.response.ProvinceResponse;
import com.webtro.modules.catalog.entity.Province;
import org.springframework.stereotype.Component;

/**
 * Chuyển {@link Province} ↔ DTO (thủ công, Builder — canonical mục 3). Cột {@code is_active} lộ ra
 * ngoài dưới nhãn nghiệp vụ {@code supported} (khu vực hỗ trợ đăng tin) {@code [§3.3]}.
 */
@Component
public class ProvinceMapper {

    /** Entity → DTO công khai. */
    public ProvinceResponse toResponse(Province entity) {
        if (entity == null) {
            return null;
        }
        return ProvinceResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .name(entity.getName())
                .shortName(entity.getShortName())
                .slug(entity.getSlug())
                .type(entity.getType())
                .supported(entity.getIsActive())
                .listingCount(entity.getListingCount())
                .build();
    }

    /** Entity → DTO quản trị (kèm số quận/huyện đang hoạt động, đã tính sẵn). */
    public AdminProvinceResponse toAdminResponse(Province entity, int districtCount) {
        if (entity == null) {
            return null;
        }
        return AdminProvinceResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .name(entity.getName())
                .slug(entity.getSlug())
                .type(entity.getType())
                .supported(entity.getIsActive())
                .districtCount(districtCount)
                .listingCount(entity.getListingCount())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
