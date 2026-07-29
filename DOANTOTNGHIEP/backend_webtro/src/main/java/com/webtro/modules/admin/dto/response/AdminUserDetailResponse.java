package com.webtro.modules.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Chi tiết một người dùng cho màn quản trị — {@code GET /api/admin/users/{id}} (canonical mục 4.13).
 * Gộp hồ sơ tài khoản, hồ sơ người thuê và (nếu có) hồ sơ chủ trọ kèm các chỉ số liên quan. Đây là
 * góc nhìn quản trị nên KHÔNG che email/SĐT (khác {@code LandlordPublicResponse}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "AdminUserDetailResponse", description = "Chi tiết người dùng (góc nhìn quản trị)")
public class AdminUserDetailResponse {

    // -------- Tài khoản --------
    private Long id;
    private String fullName;
    private String email;
    private String phone;
    private String avatarUrl;
    private String gender;

    private List<String> roles;
    private String status;
    private String statusLabel;
    private Boolean emailVerified;
    private Boolean phoneVerified;

    private String lockReason;
    private Instant lockedAt;
    private Long lockedBy;

    private Instant createdAt;
    private Instant lastLoginAt;

    // -------- Hồ sơ người thuê (user_profiles) --------
    private LocalDate dateOfBirth;
    private String bio;
    private String occupation;
    private String address;

    // -------- Hồ sơ chủ trọ (landlord_profiles, null nếu không phải chủ trọ) --------
    private LandlordDetail landlordProfile;

    /**
     * Khối thông tin chủ trọ nhúng — chỉ xuất hiện khi người dùng có hồ sơ chủ trọ.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "AdminLandlordDetail", description = "Chi tiết hồ sơ chủ trọ (góc nhìn quản trị)")
    public static class LandlordDetail {

        private String businessName;
        private String displayName;
        private String contactName;
        private String contactPhone;
        private String contactEmail;

        private String verificationStatus;
        private String verificationStatusLabel;
        private Instant verifiedAt;
        private Long verifiedById;
        private String verificationNote;

        private Integer trustScore;
        private BigDecimal averageRating;
        private Integer reviewCount;
        private Integer responseRatePercent;

        private Integer totalListings;
        private Integer activeListings;
        private Integer lockedListingCount;
        private Integer validReportCount;
        private Integer warningCount;

        private Boolean postingSuspended;
        private Instant postingSuspendedUntil;
        private String restrictReason;
    }
}
