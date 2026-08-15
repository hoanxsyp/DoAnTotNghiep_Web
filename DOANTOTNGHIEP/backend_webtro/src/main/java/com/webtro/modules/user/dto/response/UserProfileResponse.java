package com.webtro.modules.user.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.webtro.common.enums.Gender;
import com.webtro.common.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Hồ sơ cá nhân đầy đủ của chính người dùng — {@code GET/PUT /api/users/me}
 * (USER-01/02, docs/03 mục 4.2.1). Chỉ trả về cho CHÍNH chủ tài khoản (che dữ liệu §5.7 cột OWNER).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "UserProfileResponse", description = "Hồ sơ cá nhân đầy đủ của người dùng hiện tại")
public class UserProfileResponse {

    @Schema(description = "Id người dùng", example = "42")
    private Long id;

    @Schema(description = "Họ tên", example = "Nguyễn Văn An")
    private String fullName;

    @Schema(description = "Email đăng nhập", example = "nguyen.van.an@gmail.com")
    private String email;

    @Schema(description = "Số điện thoại", example = "0901234456")
    private String phone;

    @Schema(description = "URL ảnh đại diện (null nếu chưa có)")
    private String avatarUrl;

    @Schema(description = "Giới tính", example = "MALE")
    private Gender gender;

    @Schema(description = "Ngày sinh", example = "1998-05-20")
    private LocalDate dateOfBirth;

    @Schema(description = "Trạng thái tài khoản", example = "ACTIVE")
    private UserStatus status;

    @Schema(description = "Vai trò duy nhất của người dùng", example = "ROLE_TENANT")
    private String role;

    @Schema(description = "Email đã xác thực chưa", example = "true")
    private boolean emailVerified;

    @Schema(description = "Số điện thoại đã xác thực chưa", example = "false")
    private boolean phoneVerified;

    @Schema(description = "Địa chỉ thường trú")
    private String address;

    @Schema(description = "Giới thiệu bản thân")
    private String bio;

    @Schema(description = "Hồ sơ chủ trọ (chỉ có khi user có ROLE_LANDLORD)")
    private LandlordSummary landlordProfile;

    @Schema(description = "Thời điểm tạo tài khoản")
    private Instant createdAt;

    @Schema(description = "Lần đăng nhập gần nhất")
    private Instant lastLoginAt;

    /**
     * Tóm tắt hồ sơ chủ trọ nhúng trong hồ sơ cá nhân — chỉ xuất hiện khi user là chủ trọ.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "LandlordSummary", description = "Tóm tắt hồ sơ chủ trọ")
    public static class LandlordSummary {

        @Schema(description = "Đã được xác minh chưa", example = "true")
        private boolean verified;

        @Schema(description = "Thời điểm xác minh")
        private Instant verifiedAt;

        @Schema(description = "Điểm uy tín (0-100)", example = "87")
        private Integer trustScore;

        @Schema(description = "Tổng số tin đã đăng", example = "6")
        private Integer totalListings;

        @Schema(description = "Số tin đang hoạt động", example = "4")
        private Integer activeListings;

        @Schema(description = "Điểm đánh giá trung bình", example = "4.5")
        private BigDecimal averageRating;

        @Schema(description = "Cho phép chat", example = "true")
        private boolean chatEnabled;

        @Schema(description = "Tên người liên hệ", example = "Anh An")
        private String contactName;

        @Schema(description = "Số điện thoại liên hệ", example = "0901234456")
        private String contactPhone;

        @Schema(description = "Tỷ lệ phản hồi (%)", example = "92")
        private Integer responseRatePercent;
    }
}
