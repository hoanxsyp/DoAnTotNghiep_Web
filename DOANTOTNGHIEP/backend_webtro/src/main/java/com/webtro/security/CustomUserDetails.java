package com.webtro.security;

import com.webtro.common.enums.UserStatus;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Principal của Spring Security cho hệ thống. Gói thông tin định danh + quyền của người dùng đã
 * xác thực, dùng làm {@code Authentication.getPrincipal()}.
 *
 * <p>Authority gồm CẢ role ({@code ROLE_*}) và permission (RBAC 2 tầng, canonical mục 4.2), nên
 * {@code hasRole()} và {@code hasAuthority()} đều dùng được ở {@code @PreAuthorize}.
 */
@Getter
public class CustomUserDetails implements UserDetails {

    private final Long id;
    private final String email;
    private final String passwordHash;
    private final UserStatus status;
    private final List<String> roles;
    private final List<String> permissions;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(Long id, String email, String passwordHash, UserStatus status,
                             List<String> roles, List<String> permissions) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.status = status;
        this.roles = roles == null ? List.of() : List.copyOf(roles);
        this.permissions = permissions == null ? List.of() : List.copyOf(permissions);
        this.authorities = Stream.concat(this.roles.stream(), this.permissions.stream())
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    /** Spring Security dùng "username" — ở đây là email. */
    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.LOCKED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }
}
