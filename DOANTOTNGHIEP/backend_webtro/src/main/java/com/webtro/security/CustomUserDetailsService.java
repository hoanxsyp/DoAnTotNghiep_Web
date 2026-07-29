package com.webtro.security;

import com.webtro.modules.user.entity.User;
import com.webtro.modules.user.repository.PermissionRepository;
import com.webtro.modules.user.repository.RoleRepository;
import com.webtro.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Nạp {@link CustomUserDetails} cho Spring Security. Dùng ở 2 nơi:
 * <ul>
 *   <li>Đăng nhập bằng email/mật khẩu ({@link #loadUserByUsername}).</li>
 *   <li>Xác thực token: {@link JwtAuthenticationFilter} nạp lại user theo id để chắc chắn trạng
 *       thái mới nhất (tài khoản có thể đã bị khóa sau khi token phát hành).</li>
 * </ul>
 *
 * <p>Authority = danh sách role code + permission code, suy ra qua RBAC 2 tầng (canonical mục 4.2).
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản: " + email));
        return toUserDetails(user);
    }

    @Transactional(readOnly = true)
    public CustomUserDetails loadById(Long userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản id: " + userId));
        return toUserDetails(user);
    }

    private CustomUserDetails toUserDetails(User user) {
        List<String> roles = roleRepository.findRoleCodesByUserId(user.getId());
        List<String> permissions = permissionRepository.findPermissionCodesByUserId(user.getId());
        return new CustomUserDetails(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getStatus(),
                roles,
                permissions);
    }
}
