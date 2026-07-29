package com.webtro.modules.user.mapper;

import com.webtro.common.enums.TrustLabel;
import com.webtro.modules.user.dto.response.FollowingItemResponse;
import com.webtro.modules.user.entity.Follow;
import com.webtro.modules.user.entity.LandlordProfile;
import com.webtro.modules.user.entity.User;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Ánh xạ {@link Follow} ra mục danh sách đang theo dõi (canonical luật 3).
 */
@Component
public class FollowMapper {

    /**
     * Một mục trong danh sách đang theo dõi.
     *
     * @param follow     quan hệ theo dõi
     * @param landlord   chủ trọ được theo dõi
     * @param lp         hồ sơ chủ trọ (có thể null)
     * @param trustLabel nhãn uy tín đã tính sẵn (null nếu không có hồ sơ)
     */
    public FollowingItemResponse toItem(Follow follow, User landlord, LandlordProfile lp, TrustLabel trustLabel) {
        FollowingItemResponse.FollowingItemResponseBuilder builder = FollowingItemResponse.builder()
                .landlordId(landlord.getId())
                .fullName(landlord.getFullName())
                .avatarUrl(landlord.getAvatarUrl())
                .followedAt(follow.getCreatedAt());

        if (lp != null) {
            builder.verified(lp.getVerifiedAt() != null)
                    .trustScore(toInt(lp.getTrustScore()))
                    .trustLabel(trustLabel)
                    .averageRating(lp.getAverageRating())
                    .activeListingCount(lp.getTotalActiveListings());
        } else {
            builder.verified(false)
                    .activeListingCount(0);
        }
        return builder.build();
    }

    private Integer toInt(BigDecimal value) {
        return value == null ? null : value.setScale(0, RoundingMode.HALF_UP).intValue();
    }
}
