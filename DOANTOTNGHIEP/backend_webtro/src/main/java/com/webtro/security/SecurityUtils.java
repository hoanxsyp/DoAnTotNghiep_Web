package com.webtro.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Truy cập người dùng đang đăng nhập từ SecurityContext ở tầng service.
 *
 * <p>Dùng khi cần biết "ai đang thao tác" mà không muốn nhận {@code CustomUserDetails} qua tham
 * số controller (ví dụ trong service gọi lồng nhau). Với controller, ưu tiên
 * {@code @AuthenticationPrincipal CustomUserDetails}.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<CustomUserDetails> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof CustomUserDetails userDetails)) {
            return Optional.empty();
        }
        return Optional.of(userDetails);
    }

    public static Optional<Long> getCurrentUserId() {
        return getCurrentUser().map(CustomUserDetails::getId);
    }

    public static boolean isAuthenticated() {
        return getCurrentUser().isPresent();
    }

    public static boolean hasPermission(String permission) {
        return getCurrentUser().map(u -> u.hasPermission(permission)).orElse(false);
    }
}
