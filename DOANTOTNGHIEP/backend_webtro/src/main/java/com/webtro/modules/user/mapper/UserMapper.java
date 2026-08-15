package com.webtro.modules.user.mapper;

import com.webtro.modules.user.dto.response.UserProfileResponse;
import com.webtro.modules.user.dto.response.UserRefResponse;
import com.webtro.modules.user.entity.LandlordProfile;
import com.webtro.modules.user.entity.User;
import com.webtro.modules.user.entity.UserProfile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Chuyển đổi {@link User} ↔ DTO (thủ công, dùng Builder — canonical luật 3, không MapStruct).
 * Đây là nơi DUY NHẤT ánh xạ entity người dùng ra DTO.
 */
@Component
public class UserMapper {

    /**
     * Dựng hồ sơ cá nhân đầy đủ cho chính chủ tài khoản (USER-01).
     *
     * @param user            người dùng
     * @param profile         hồ sơ người thuê (có thể null nếu chưa tạo)
     * @param landlordProfile hồ sơ chủ trọ (null nếu không phải chủ trọ)
     * @param role            mã vai trò duy nhất
     */
    public UserProfileResponse toProfileResponse(User user, UserProfile profile,
                                                 LandlordProfile landlordProfile,
                                                 String role) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .gender(user.getGender())
                .dateOfBirth(profile != null ? profile.getDateOfBirth() : null)
                .status(user.getStatus())
                .role(role)
                .emailVerified(user.getEmailVerifiedAt() != null)
                .phoneVerified(user.getPhoneVerifiedAt() != null)
                .address(profile != null ? profile.getAddressDetail() : null)
                .bio(profile != null ? profile.getBio() : null)
                .landlordProfile(landlordProfile != null ? toLandlordSummary(landlordProfile) : null)
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }

    /** Tóm tắt hồ sơ chủ trọ nhúng trong hồ sơ cá nhân. */
    public UserProfileResponse.LandlordSummary toLandlordSummary(LandlordProfile lp) {
        return UserProfileResponse.LandlordSummary.builder()
                .verified(lp.getVerifiedAt() != null)
                .verifiedAt(lp.getVerifiedAt())
                .trustScore(toInt(lp.getTrustScore()))
                .totalListings(lp.getTotalListings())
                .activeListings(lp.getTotalActiveListings())
                .averageRating(lp.getAverageRating())
                .chatEnabled(Boolean.TRUE.equals(lp.getAllowChat()))
                .contactName(lp.getContactName())
                .contactPhone(lp.getContactPhone())
                .responseRatePercent(lp.getResponseRatePercent())
                .build();
    }

    /** Tham chiếu rút gọn cho module khác (tên + avatar chủ trọ/tác giả). */
    public UserRefResponse toRef(User user) {
        return UserRefResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }

    private Integer toInt(BigDecimal value) {
        return value == null ? null : value.setScale(0, java.math.RoundingMode.HALF_UP).intValue();
    }
}
