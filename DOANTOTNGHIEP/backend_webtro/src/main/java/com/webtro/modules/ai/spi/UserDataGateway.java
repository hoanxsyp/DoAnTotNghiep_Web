package com.webtro.modules.ai.spi;

import com.webtro.common.enums.GenderRequirement;

import java.util.Optional;

/**
 * Cổng (SPI) mà module {@code ai} cần từ module {@code user} — canonical luật 4. Module {@code user}
 * phải cung cấp một {@code @Component} hiện thực interface này (bọc {@code UserProfileRepository}).
 *
 * <p>Dùng cho: lấy hai chiều sở thích khai báo của người dùng phục vụ recommendation §9.2
 * (số người ở, giới tính ở ghép) — hai chiều v1 bỏ sót (canonical mục 10.2).
 */
public interface UserDataGateway {

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
