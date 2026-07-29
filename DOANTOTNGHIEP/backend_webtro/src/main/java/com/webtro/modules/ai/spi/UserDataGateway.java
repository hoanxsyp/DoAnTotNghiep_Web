package com.webtro.modules.ai.spi;

import com.webtro.common.enums.GenderRequirement;

import java.time.Instant;
import java.util.Optional;

/**
 * Cổng (SPI) mà module {@code ai} cần từ module {@code user} — canonical luật 4. Module {@code user}
 * phải cung cấp một {@code @Component} hiện thực interface này (bọc {@code UserRepository} +
 * {@code UserProfileRepository}).
 *
 * <p>Dùng cho: (1) tính "tài khoản mới" để giảm trọng số bình luận trong sentiment §9.1;
 * (2) lấy hai chiều sở thích khai báo của người dùng phục vụ recommendation §9.2
 * (số người ở, giới tính ở ghép) — hai chiều v1 bỏ sót (canonical mục 10.2).
 */
public interface UserDataGateway {

    /** Thời điểm tạo tài khoản (để suy ra tuổi tài khoản); rỗng nếu không tồn tại. */
    Optional<Instant> accountCreatedAt(Long userId);

    /**
     * Hai chiều sở thích khai báo tại {@code user_profiles}.
     *
     * @param preferredOccupants        số người ở mong muốn (có thể {@code null})
     * @param preferredGenderRequirement giới tính ở ghép mong muốn (có thể {@code null})
     */
    record UserPreference(Integer preferredOccupants, GenderRequirement preferredGenderRequirement) {
    }

    /** Sở thích khai báo của người dùng; rỗng nếu chưa có hồ sơ. */
    Optional<UserPreference> preference(Long userId);
}
