package com.webtro.modules.user.adapter;

import com.webtro.modules.ai.spi.UserDataGateway;
import com.webtro.modules.user.entity.User;
import com.webtro.modules.user.entity.UserProfile;
import com.webtro.modules.user.repository.UserProfileRepository;
import com.webtro.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Adapter (module {@code user}) hiện thực {@link UserDataGateway} mà module {@code ai} cần —
 * canonical luật 4. Bọc {@link UserRepository} + {@link UserProfileRepository}, chỉ đọc, ánh xạ
 * dữ liệu thô sang record của SPI.
 */
@Component
@RequiredArgsConstructor
public class UserDataGatewayAdapter implements UserDataGateway {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    /** Thời điểm tạo tài khoản còn sống; rỗng nếu không tồn tại. */
    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> accountCreatedAt(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return userRepository.findByIdAndDeletedAtIsNull(userId).map(User::getCreatedAt);
    }

    /**
     * Sở thích khai báo tại {@code user_profiles}. Schema {@code user_profiles} chỉ có
     * {@code preferred_gender_requirement} (không có cột {@code preferred_occupants}) nên
     * {@code preferredOccupants} luôn {@code null}. Rỗng nếu người dùng chưa có hồ sơ.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<UserPreference> preference(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return userProfileRepository.findByUser_IdAndDeletedAtIsNull(userId)
                .map(this::toPreference);
    }

    private UserPreference toPreference(UserProfile profile) {
        return new UserPreference(null, profile.getPreferredGenderRequirement());
    }
}
