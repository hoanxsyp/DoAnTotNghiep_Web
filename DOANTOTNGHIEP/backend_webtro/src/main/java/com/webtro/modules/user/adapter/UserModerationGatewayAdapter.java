package com.webtro.modules.user.adapter;

import com.webtro.constant.ErrorCode;
import com.webtro.constant.RoleCode;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.moderation.spi.UserModerationGateway;
import com.webtro.modules.user.entity.User;
import com.webtro.modules.user.repository.LandlordProfileRepository;
import com.webtro.modules.user.repository.RoleRepository;
import com.webtro.modules.user.repository.UserRepository;
import com.webtro.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;

/**
 * Adapter (module {@code user}) hiện thực {@link UserModerationGateway} mà module {@code moderation}
 * cần — canonical luật 4. Bọc {@link UserRepository} + {@link LandlordProfileRepository} +
 * {@link RoleRepository} để đọc thông tin kiểm duyệt, và ủy quyền thao tác ghi (tạm khóa đăng tin)
 * cho {@link UserService}.
 */
@Component
@RequiredArgsConstructor
public class UserModerationGatewayAdapter implements UserModerationGateway {

    private static final int DEFAULT_TRUST_SCORE = 100;

    private final UserRepository userRepository;
    private final LandlordProfileRepository landlordProfileRepository;
    private final RoleRepository roleRepository;
    private final UserService userService;

    /** Thông tin người dùng còn sống; ném {@code USER_NOT_FOUND} nếu không có. */
    @Override
    @Transactional(readOnly = true)
    public UserRef getRef(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .map(this::toRef)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
    }

    /** Thông tin người dùng nếu tồn tại (không ném). */
    @Override
    @Transactional(readOnly = true)
    public Optional<UserRef> findRef(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return userRepository.findByIdAndDeletedAtIsNull(userId).map(this::toRef);
    }

    /** Người dùng còn sống có tồn tại không. */
    @Override
    @Transactional(readOnly = true)
    public boolean existsById(Long userId) {
        return userId != null && userRepository.findByIdAndDeletedAtIsNull(userId).isPresent();
    }

    /** Ủy quyền tạm khóa đăng tin cho {@link UserService} (đặt {@code posting_restricted_until}). */
    @Override
    public void suspendPosting(Long userId, Instant until, String reason) {
        userService.suspendPosting(userId, until, reason);
    }

    /**
     * Dựng {@link UserRef}. {@code trustScore} lấy từ hồ sơ chủ trọ (làm tròn về số nguyên); 100 nếu
     * chưa phải chủ trọ. {@code isAdmin} khi có vai trò {@code ROLE_ADMIN}.
     */
    private UserRef toRef(User user) {
        int trustScore = landlordProfileRepository.findByUser_IdAndDeletedAtIsNull(user.getId())
                .map(lp -> toInt(lp.getTrustScore()))
                .orElse(DEFAULT_TRUST_SCORE);
        boolean isAdmin = user.getRole() != null && RoleCode.ADMIN.equals(user.getRole().getCode());
        return new UserRef(user.getId(), user.getFullName(), user.getEmail(),
                user.getCreatedAt(), trustScore, isAdmin);
    }

    private int toInt(BigDecimal value) {
        return value == null ? DEFAULT_TRUST_SCORE : value.setScale(0, RoundingMode.HALF_UP).intValue();
    }
}
