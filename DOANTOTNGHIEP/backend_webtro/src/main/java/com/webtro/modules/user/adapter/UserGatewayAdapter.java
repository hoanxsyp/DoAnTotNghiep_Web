package com.webtro.modules.user.adapter;

import com.webtro.common.enums.VerificationStatus;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.interaction.spi.UserGateway;
import com.webtro.modules.user.entity.LandlordProfile;
import com.webtro.modules.user.entity.User;
import com.webtro.modules.user.repository.LandlordProfileRepository;
import com.webtro.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Adapter (module {@code user}) hiện thực {@link UserGateway} mà module {@code interaction} cần —
 * canonical luật 4. Bọc {@link UserRepository} + {@link LandlordProfileRepository}: cung cấp thông
 * tin hiển thị tác giả bình luận/đánh giá, thông tin liên hệ chủ trọ, cờ hạn chế bình luận/liên hệ,
 * và tổng hợp đánh giá chủ trọ.
 */
@Component
@RequiredArgsConstructor
public class UserGatewayAdapter implements UserGateway {

    private final UserRepository userRepository;
    private final LandlordProfileRepository landlordProfileRepository;

    /** Thông tin liên hệ công khai của chủ trọ; ném {@code USER_NOT_FOUND} nếu không có hồ sơ chủ trọ. */
    @Override
    @Transactional(readOnly = true)
    public LandlordContactInfo getLandlordContactInfo(Long landlordId) {
        LandlordProfile lp = landlordProfileRepository.findByUser_IdAndDeletedAtIsNull(landlordId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        User user = lp.getUser();
        String landlordName = lp.getDisplayName() != null && !lp.getDisplayName().isBlank()
                ? lp.getDisplayName()
                : (user != null ? user.getFullName() : null);
        // Schema landlord_profiles chưa có cột Zalo riêng → trả trùng số liên hệ (canonical 4.6.1).
        return new LandlordContactInfo(
                landlordId,
                landlordName,
                lp.getContactName(),
                lp.getContactPhone(),
                lp.getContactPhone(),
                Boolean.TRUE.equals(lp.getAllowChat()),
                lp.getVerificationStatus() == VerificationStatus.VERIFIED);
    }

    /** Thông tin người dùng còn sống; ném {@code USER_NOT_FOUND} nếu không có. */
    @Override
    @Transactional(readOnly = true)
    public UserBrief getBrief(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .map(this::toBrief)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
    }

    /** Thông tin người dùng nếu tồn tại. */
    @Override
    @Transactional(readOnly = true)
    public Optional<UserBrief> findBrief(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return userRepository.findByIdAndDeletedAtIsNull(userId).map(this::toBrief);
    }

    /** Nạp hàng loạt (tránh N+1); chỉ trả các user còn sống. */
    @Override
    @Transactional(readOnly = true)
    public Map<Long, UserBrief> getBriefs(Collection<Long> userIds) {
        Map<Long, UserBrief> result = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }
        for (User user : userRepository.findAllById(userIds)) {
            if (user.getDeletedAt() == null) {
                result.put(user.getId(), toBrief(user));
            }
        }
        return result;
    }

    /** Chủ trọ có bật chat không; false nếu không có hồ sơ chủ trọ. */
    @Override
    @Transactional(readOnly = true)
    public boolean isLandlordChatEnabled(Long landlordId) {
        return landlordProfileRepository.findByUser_IdAndDeletedAtIsNull(landlordId)
                .map(lp -> Boolean.TRUE.equals(lp.getAllowChat()))
                .orElse(false);
    }

    /** Đang bị hạn chế liên hệ không ({@code users.contact_restricted_until} còn hiệu lực). */
    @Override
    @Transactional(readOnly = true)
    public boolean isContactRestricted(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .map(u -> isEffective(u.getContactRestrictedUntil()))
                .orElse(false);
    }

    /** Đang bị tạm khóa bình luận không ({@code users.comment_restricted_until} còn hiệu lực). */
    @Override
    @Transactional(readOnly = true)
    public boolean isCommentSuspended(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .map(u -> isEffective(u.getCommentRestrictedUntil()))
                .orElse(false);
    }

    /** Tổng hợp đánh giá hiện tại của chủ trọ; 0/0 nếu không có hồ sơ. */
    @Override
    @Transactional(readOnly = true)
    public LandlordAggregate getLandlordAggregate(Long landlordId) {
        return landlordProfileRepository.findByUser_IdAndDeletedAtIsNull(landlordId)
                .map(lp -> new LandlordAggregate(
                        lp.getAverageRating() != null ? lp.getAverageRating() : BigDecimal.ZERO,
                        lp.getReviewCount() != null ? lp.getReviewCount() : 0))
                .orElseGet(() -> new LandlordAggregate(BigDecimal.ZERO, 0));
    }

    /** Cập nhật {@code landlord_profiles.average_rating} + {@code review_count} (canonical 4.7.8). */
    @Override
    @Transactional
    public void updateLandlordReviewAggregate(Long landlordId, BigDecimal averageRating, int reviewCount) {
        LandlordProfile lp = landlordProfileRepository.findByUser_IdAndDeletedAtIsNull(landlordId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        lp.setAverageRating(averageRating != null ? averageRating : BigDecimal.ZERO);
        lp.setReviewCount(reviewCount);
        landlordProfileRepository.save(lp);
    }

    private UserBrief toBrief(User user) {
        return new UserBrief(user.getId(), user.getFullName(), user.getAvatarUrl(),
                user.getPhone(), user.getCreatedAt());
    }

    /** Một mốc hạn chế còn hiệu lực khi không null và ở tương lai. */
    private boolean isEffective(Instant until) {
        return until != null && until.isAfter(Instant.now());
    }
}
