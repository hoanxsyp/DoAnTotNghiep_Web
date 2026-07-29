package com.webtro.modules.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.webtro.common.enums.TrustLabel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Một chủ trọ trong màn quản lý chủ trọ (canonical 4.13.6). Chỉ số uy tín/vi phạm/tin lấy từ hồ sơ
 * chủ trọ ({@code landlord_profiles}, các cột đếm sẵn §5.8).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "AdminLandlordResponse", description = "Chủ trọ (góc nhìn quản trị)")
public class AdminLandlordResponse {

    @Schema(description = "Id người dùng", example = "42")
    private Long userId;

    private String fullName;
    private String email;
    private String phone;
    private String avatarUrl;

    @Schema(description = "Tên cơ sở/doanh nghiệp", example = "Nhà trọ An Bình")
    private String businessName;

    @Schema(description = "Tên hiển thị công khai")
    private String displayName;

    @Schema(description = "Trạng thái xác thực", example = "VERIFIED")
    private String verificationStatus;

    @Schema(description = "Nhãn trạng thái xác thực (tiếng Việt)")
    private String verificationStatusLabel;

    private Instant verifiedAt;
    private Long verifiedById;
    private String verifiedByName;

    @Schema(description = "Điểm uy tín (0-100)", example = "87")
    private Integer trustScore;

    @Schema(description = "Nhãn uy tín suy ra từ điểm")
    private TrustLabel trustLabel;

    @Schema(description = "Tổng số tin đã đăng", example = "6")
    private Integer listingCount;

    @Schema(description = "Số tin đang hoạt động", example = "4")
    private Integer activeListingCount;

    @Schema(description = "Số tin bị khóa", example = "0")
    private Integer lockedListingCount;

    private BigDecimal averageRating;
    private Integer reviewCount;

    @Schema(description = "Số báo cáo hợp lệ đã nhận", example = "0")
    private Integer validReportCount;

    @Schema(description = "Số lần bị cảnh báo vi phạm", example = "0")
    private Integer warningCount;

    @Schema(description = "Đang bị hạn chế đăng tin không", example = "false")
    private Boolean postingSuspended;

    @Schema(description = "Hạn chế đăng tin đến thời điểm")
    private Instant postingSuspendedUntil;

    @Schema(description = "Thời điểm tạo tài khoản")
    private Instant createdAt;
}
