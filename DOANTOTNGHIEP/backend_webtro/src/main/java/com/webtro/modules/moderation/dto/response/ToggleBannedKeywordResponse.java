package com.webtro.modules.moderation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/**
 * Kết quả bật/tắt từ khóa cấm (canonical 4.20.8).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ToggleBannedKeywordResponse", description = "Kết quả bật/tắt từ khóa cấm")
public class ToggleBannedKeywordResponse {

    private Long id;
    private String keyword;
    private boolean active;
    private boolean previousActive;
    private long hitCount;
    private List<String> cacheInvalidated;
    private Instant updatedAt;
}
