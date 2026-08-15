package com.webtro.security;

import com.webtro.common.enums.UserStatus;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Principal của Spring Security cho hệ thống. Gói thông tin định danh + quyền của người dùng đã
 * xác thực, dùng làm {@code Authentication.getPrincipal()}.
 *
 * <p>Authority chỉ gồm role ({@code ROLE_*}). Mỗi người dùng có ĐÚNG MỘT role; mọi phân quyền
 * backend dùng {@code hasRole()} / {@code hasAnyRole()} để bảo đảm hai người cùng role luôn có
 * cùng bộ chức năng.
 */
@Getter
public class CustomUserDetails implements UserDetails {

    private final Long id;
    private final String email;
    private final String passwordHash;
    private final UserStatus status;
    private final String role;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(Long id, String email, String passwordHash, UserStatus status,
                             String role) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.status = status;
        this.role = role;
        this.authorities = (role == null || role.isBlank() ? List.<String>of() : List.of(role)).stream()
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

    public boolean hasRole(String expectedRole) {
        return role != null && role.equals(expectedRole);
    }
}
