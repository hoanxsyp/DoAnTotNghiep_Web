package com.webtro.security;

import com.webtro.constant.RoleCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
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

    public static Optional<String> getCurrentRole() {
        return getCurrentUser().map(CustomUserDetails::getRole);
    }

    public static boolean hasRole(String role) {
        return getCurrentRole().map(role::equals).orElse(false);
    }

    public static boolean hasAnyRole(String... roles) {
        return getCurrentRole()
                .map(current -> Arrays.asList(roles).contains(current))
                .orElse(false);
    }

    public static boolean isAdmin() {
        return hasRole(RoleCode.ADMIN);
    }
}
