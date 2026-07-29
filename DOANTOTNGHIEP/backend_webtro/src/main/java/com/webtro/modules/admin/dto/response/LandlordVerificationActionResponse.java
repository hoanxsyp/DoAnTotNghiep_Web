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
 * Kết quả thao tác xác thực (4.13.7) / hủy xác thực (4.13.8) chủ trọ. Các field không liên quan tới
 * thao tác hiện tại bị loại khỏi JSON ({@code NON_NULL}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "LandlordVerificationActionResponse", description = "Kết quả xác thực/hủy xác thực chủ trọ")
public class LandlordVerificationActionResponse {

    private Long userId;
    private String verificationStatus;
    private String previousStatus;

    private Long verifiedById;
    private String verifiedByName;
    private Instant verifiedAt;

    /** Lý do (chỉ có ở thao tác hủy xác thực). */
    private String reason;

    private Long auditLogId;
    private Boolean userNotified;
    private Instant updatedAt;
}
