package com.webtro.modules.user.mapper;

import com.webtro.common.enums.TrustLabel;
import com.webtro.modules.user.dto.response.LandlordPublicResponse;
import com.webtro.modules.user.dto.response.MyLandlordProfileResponse;
import com.webtro.modules.user.entity.LandlordProfile;
import com.webtro.modules.user.entity.User;
import com.webtro.util.MaskUtil;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Ánh xạ {@link LandlordProfile} ra các DTO công khai/riêng tư (canonical luật 3).
 * Thực thi bảng che dữ liệu §5.7: che số điện thoại với khách chưa đăng nhập.
 */
@Component
public class LandlordProfileMapper {

    /**
     * Hồ sơ chủ trọ công khai (USER-04). Áp dụng che số điện thoại theo {@code viewerAuthenticated}.
     *
     * @param user                người dùng (chủ trọ)
     * @param lp                  hồ sơ chủ trọ (null nếu user không phải chủ trọ)
     * @param isLandlord          user có phải chủ trọ không
     * @param followerCount       số người theo dõi
     * @param followedByMe        người xem có đang theo dõi không
     * @param viewerAuthenticated người xem đã đăng nhập chưa (quyết định che số điện thoại)
     * @param trustLabel          nhãn uy tín đã tính sẵn (null nếu không phải chủ trọ)
     */
    public LandlordPublicResponse toPublicResponse(User user, LandlordProfile lp, boolean isLandlord,
                                                   long followerCount, boolean followedByMe,
                                                   boolean viewerAuthenticated, TrustLabel trustLabel) {
        LandlordPublicResponse.LandlordPublicResponseBuilder builder = LandlordPublicResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .isLandlord(isLandlord)
                .followerCount(followerCount)
                .followedByMe(followedByMe)
                .memberSince(user.getCreatedAt());

        if (lp != null) {
            String rawPhone = lp.getContactPhone() != null ? lp.getContactPhone() : user.getPhone();
            builder.verified(lp.getVerifiedAt() != null)
                    .trustScore(toInt(lp.getTrustScore()))
                    .trustLabel(trustLabel)
                    .averageRating(lp.getAverageRating())
                    .totalReviews(lp.getReviewCount())
                    .totalActiveListings(lp.getTotalActiveListings())
                    .totalClosedListings(closedListings(lp))
                    .contactName(lp.getContactName())
                    .contactPhone(maskIfNeeded(rawPhone, viewerAuthenticated))
                    .phoneMasked(rawPhone != null && !viewerAuthenticated)
                    .chatEnabled(Boolean.TRUE.equals(lp.getAllowChat()))
                    .responseRatePercent(lp.getResponseRatePercent());
        } else {
            // Người dùng thường (không phải chủ trọ): chỉ trả thông tin công khai tối thiểu.
            builder.verified(false)
                    .trustScore(null)
                    .averageRating(null)
                    .totalReviews(0)
                    .totalActiveListings(0)
                    .totalClosedListings(0)
                    .phoneMasked(false);
        }
        return builder.build();
    }

    /** Hồ sơ chủ trọ của chính mình (mục 4.2.10). */
    public MyLandlordProfileResponse toMyResponse(LandlordProfile lp, boolean postingSuspended,
                                                  Instant postingSuspendedUntil) {
        return MyLandlordProfileResponse.builder()
                .userId(lp.getUser().getId())
                .contactName(lp.getContactName())
                .contactPhone(lp.getContactPhone())
                .contactEmail(lp.getContactEmail())
                .displayName(lp.getDisplayName())
                .businessName(lp.getCompanyName())
                .businessAddress(lp.getAddress())
                .chatEnabled(Boolean.TRUE.equals(lp.getAllowChat()))
                .verificationStatus(lp.getVerificationStatus())
                .verifiedAt(lp.getVerifiedAt())
                .trustScore(toInt(lp.getTrustScore()))
                .totalListings(lp.getTotalListings())
                .activeListings(lp.getTotalActiveListings())
                .postingSuspended(postingSuspended)
                .postingSuspendedUntil(postingSuspendedUntil)
                .warningCount(lp.getWarningCount())
                .averageRating(lp.getAverageRating())
                .build();
    }

    private Integer closedListings(LandlordProfile lp) {
        int total = lp.getTotalListings() != null ? lp.getTotalListings() : 0;
        int active = lp.getTotalActiveListings() != null ? lp.getTotalActiveListings() : 0;
        return Math.max(0, total - active);
    }

    private String maskIfNeeded(String phone, boolean viewerAuthenticated) {
        if (phone == null) {
            return null;
        }
        return viewerAuthenticated ? phone : MaskUtil.maskPhone(phone);
    }

    private Integer toInt(BigDecimal value) {
        return value == null ? null : value.setScale(0, RoundingMode.HALF_UP).intValue();
    }
}
