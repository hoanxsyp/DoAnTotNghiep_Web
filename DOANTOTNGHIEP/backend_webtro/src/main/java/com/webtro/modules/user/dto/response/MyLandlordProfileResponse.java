package com.webtro.modules.user.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.webtro.common.enums.VerificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Hồ sơ chủ trọ của chính mình — {@code GET/PUT /api/users/me/landlord-profile}
 * (docs/03 mục 4.2.10, {@code [§7.3]}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "MyLandlordProfileResponse", description = "Hồ sơ chủ trọ của người dùng hiện tại")
public class MyLandlordProfileResponse {

    @Schema(description = "Id chủ trọ", example = "42")
    private Long userId;

    @Schema(description = "Tên người liên hệ", example = "Anh An")
    private String contactName;

    @Schema(description = "Số điện thoại liên hệ", example = "0901234456")
    private String contactPhone;

    @Schema(description = "Email liên hệ", example = "an.nguyen@gmail.com")
    private String contactEmail;

    @Schema(description = "Tên hiển thị công khai", example = "Nhà trọ An Bình")
    private String displayName;

    @Schema(description = "Tên cơ sở/doanh nghiệp", example = "Nhà trọ An Bình")
    private String businessName;

    @Schema(description = "Địa chỉ cơ sở", example = "45/12 Đường D2, P.25, Q. Bình Thạnh, TP. Hồ Chí Minh")
    private String businessAddress;

    @Schema(description = "Cho phép chat", example = "true")
    private boolean chatEnabled;

    @Schema(description = "Trạng thái xác minh", example = "VERIFIED")
    private VerificationStatus verificationStatus;

    @Schema(description = "Thời điểm xác minh")
    private Instant verifiedAt;

    @Schema(description = "Điểm uy tín (0-100)", example = "87")
    private Integer trustScore;

    @Schema(description = "Tổng số tin đã đăng", example = "6")
    private Integer totalListings;

    @Schema(description = "Số tin đang hoạt động", example = "4")
    private Integer activeListings;

    @Schema(description = "Đang bị tạm khóa đăng tin không", example = "false")
    private boolean postingSuspended;

    @Schema(description = "Tạm khóa đăng tin đến thời điểm này (null nếu không bị khóa)")
    private Instant postingSuspendedUntil;

    @Schema(description = "Số lần bị cảnh báo vi phạm (tích lũy)", example = "0")
    private Integer warningCount;

    @Schema(description = "Điểm đánh giá trung bình", example = "4.5")
    private BigDecimal averageRating;
}
