package com.webtro.modules.user.dto.response;

import com.webtro.common.enums.VerificationStatus;
import com.webtro.common.enums.VerificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Kết quả gửi yêu cầu xác thực chủ trọ —
 * {@code POST /api/users/me/landlord-verification} (USER-06, docs/03 mục 4.2.12).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "LandlordVerificationResponse", description = "Kết quả gửi yêu cầu xác thực chủ trọ")
public class LandlordVerificationResponse {

    @Schema(description = "Id bản ghi xác thực", example = "77")
    private Long verificationId;

    @Schema(description = "Loại xác thực", example = "LANDLORD")
    private VerificationType type;

    @Schema(description = "Trạng thái", example = "PENDING")
    private VerificationStatus status;

    @Schema(description = "Thời điểm gửi yêu cầu")
    private Instant submittedAt;
}
