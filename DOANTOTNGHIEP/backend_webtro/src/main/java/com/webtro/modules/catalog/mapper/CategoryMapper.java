package com.webtro.modules.catalog.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webtro.modules.catalog.dto.response.CategoryResponse;
import com.webtro.modules.catalog.entity.Category;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Chuyển {@link Category} ↔ DTO (thủ công, Builder — không MapStruct, canonical mục 3).
 *
 * <p>Hai cột {@code required_fields}/{@code optional_fields} lưu chuỗi JSON trong DB nhưng lộ ra
 * API dưới dạng {@code List<String>}, nên mapper chịu trách nhiệm serialize/deserialize bằng
 * {@link ObjectMapper} dùng chung của Spring Boot.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CategoryMapper {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    /** Entity → DTO. */
    public CategoryResponse toResponse(Category entity) {
        if (entity == null) {
            return null;
        }
        return CategoryResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .name(entity.getName())
                .slug(entity.getSlug())
                .description(entity.getDescription())
                .iconUrl(entity.getIcon())
                .displayOrder(entity.getDisplayOrder())
                .active(entity.getIsActive())
                .listingCount(entity.getListingCount())
                .requiredFields(fromJson(entity.getRequiredFields()))
                .optionalFields(fromJson(entity.getOptionalFields()))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /** Serialize danh sách trường thành chuỗi JSON để lưu DB; null/rỗng → "[]". */
    public String toJson(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            log.warn("Không serialize được danh sách trường danh mục: {}", e.getMessage());
            return "[]";
        }
    }

    /** Deserialize chuỗi JSON trong DB thành {@code List<String>}; lỗi/parse hỏng → rỗng. */
    public List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> parsed = objectMapper.readValue(json, STRING_LIST);
            return parsed != null ? parsed : List.of();
        } catch (Exception e) {
            log.warn("Không parse được JSON trường danh mục '{}': {}", json, e.getMessage());
            return List.of();
        }
    }
}
