package com.webtro.modules.admin.dto.response;

import com.webtro.common.PageResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Kết quả xem log AI (canonical 4.19.1): module đang xem + trang dữ liệu log.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AiLogResponse", description = "Log AI theo module")
public class AiLogResponse {

    @Schema(description = "Module AI", example = "SENTIMENT")
    private String module;

    @Schema(description = "Trang dữ liệu log")
    private PageResponse<AiLogItemResponse> results;
}
