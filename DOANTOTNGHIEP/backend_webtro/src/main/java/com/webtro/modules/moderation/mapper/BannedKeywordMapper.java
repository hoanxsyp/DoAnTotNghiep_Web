package com.webtro.modules.moderation.mapper;

import com.webtro.modules.moderation.dto.response.BannedKeywordResponse;
import com.webtro.modules.moderation.entity.BannedKeyword;
import org.springframework.stereotype.Component;

/**
 * Chuyển đổi {@link BannedKeyword} sang DTO (canonical luật 3).
 */
@Component
public class BannedKeywordMapper {

    public BannedKeywordResponse toResponse(BannedKeyword k) {
        return BannedKeywordResponse.builder()
                .id(k.getId())
                .keyword(k.getKeyword())
                .normalizedKeyword(k.getNormalizedKeyword())
                .severity(k.getSeverity())
                .severityLabel(k.getSeverity().getLabel())
                .appliesTo(k.getAppliesTo())
                .appliesToLabel(k.getAppliesTo().getLabel())
                .regex(Boolean.TRUE.equals(k.getIsRegex()))
                .category(k.getCategory())
                .note(k.getNote())
                .active(Boolean.TRUE.equals(k.getIsActive()))
                .hitCount(k.getHitCount() == null ? 0 : k.getHitCount())
                .createdAt(k.getCreatedAt())
                .updatedAt(k.getUpdatedAt())
                .build();
    }
}
