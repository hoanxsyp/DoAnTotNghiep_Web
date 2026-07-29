package com.webtro.modules.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Kết quả tạm hạn chế đăng tin của chủ trọ ({@code PUT /api/admin/landlords/{id}/restrict-posting}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "LandlordPostingRestrictionResponse", description = "Kết quả hạn chế đăng tin của chủ trọ")
public class LandlordPostingRestrictionResponse {

    private Long userId;
    private boolean postingSuspended;
    private Instant postingRestrictedUntil;
    private String reason;
    private Long auditLogId;
    private Boolean userNotified;
    private Instant updatedAt;
}
