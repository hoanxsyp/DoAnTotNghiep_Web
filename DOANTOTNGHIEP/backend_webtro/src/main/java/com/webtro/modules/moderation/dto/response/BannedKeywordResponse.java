package com.webtro.modules.moderation.dto.response;

import com.webtro.common.enums.BannedKeywordScope;
import com.webtro.common.enums.BannedKeywordSeverity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Một từ khóa cấm (canonical 4.20.4).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "BannedKeywordResponse", description = "Từ khóa cấm")
public class BannedKeywordResponse {

    private Long id;
    private String keyword;
    private String normalizedKeyword;
    private BannedKeywordSeverity severity;
    private String severityLabel;
    private BannedKeywordScope appliesTo;
    private String appliesToLabel;
    private boolean regex;
    private String category;
    private String note;
    private boolean active;
    private long hitCount;
    private Instant createdAt;
    private Instant updatedAt;
}
