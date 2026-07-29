package com.webtro.modules.admin.mapper;

import com.webtro.modules.admin.dto.response.AdminUserResponse;
import com.webtro.modules.user.entity.LandlordProfile;
import com.webtro.common.enums.VerificationStatus;
import com.webtro.modules.user.entity.User;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Mapper thủ công {@link User} → {@link AdminUserResponse} (Builder, không MapStruct).
 * Nhận thêm danh sách vai trò và hồ sơ chủ trọ (có thể null) để phủ các chỉ số uy tín/vi phạm.
 */
@Component
public class AdminUserMapper {

    /**
     * @param user    tài khoản
     * @param roles   mã vai trò của tài khoản
     * @param profile hồ sơ chủ trọ (null nếu chưa là chủ trọ)
     */
    public AdminUserResponse toResponse(User user, List<String> roles, LandlordProfile profile) {
        AdminUserResponse.AdminUserResponseBuilder b = AdminUserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .roles(roles)
                .status(user.getStatus() != null ? user.getStatus().name() : null)
                .statusLabel(user.getStatus() != null ? user.getStatus().getLabel() : null)
                .emailVerified(user.getEmailVerifiedAt() != null)
                .phoneVerified(user.getPhoneVerifiedAt() != null)
                .lockReason(user.getLockReason())
                .lockedAt(user.getLockedAt())
                .lockedBy(user.getLockedBy())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt());

        if (profile != null) {
            b.landlordVerified(profile.getVerificationStatus() == VerificationStatus.VERIFIED)
                    .trustScore(toInt(profile.getTrustScore()))
                    .listingCount(profile.getTotalListings())
                    .activeListingCount(profile.getTotalActiveListings())
                    .validReportCount(profile.getValidReportCount())
                    .warningCount(profile.getWarningCount());
        } else {
            b.landlordVerified(false);
        }

        return b.build();
    }

    private Integer toInt(BigDecimal v) {
        return v != null ? v.intValue() : null;
    }
}
