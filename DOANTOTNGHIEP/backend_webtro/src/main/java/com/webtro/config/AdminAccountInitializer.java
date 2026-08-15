package com.webtro.config;

import com.webtro.common.enums.Gender;
import com.webtro.common.enums.UserStatus;
import com.webtro.config.properties.AppProperties;
import com.webtro.constant.RoleCode;
import com.webtro.modules.user.entity.Role;
import com.webtro.modules.user.entity.User;
import com.webtro.modules.user.repository.RoleRepository;
import com.webtro.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Tạo tài khoản quản trị khởi tạo lúc ứng dụng khởi động — từ biến môi trường
 * {@code ADMIN_EMAIL}/{@code ADMIN_PASSWORD} (canonical mục 6, README).
 *
 * <p><b>Vì sao seed bằng code chứ không bằng SQL:</b> bcrypt hash không sinh được trong SQL, và
 * nhúng một hash cố định vào migration là hardcode bí mật (trái yêu cầu đề bài). Ở đây mật khẩu
 * được hash bằng {@link PasswordEncoder} (BCrypt cost 12) lúc chạy.
 *
 * <p>An toàn: bỏ qua nếu {@code seedEnabled=false}, nếu mật khẩu rỗng (KHÔNG bao giờ tạo tài khoản
 * mật khẩu rỗng), hoặc nếu tài khoản đã tồn tại (idempotent).
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

    private final AppProperties appProperties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Admin admin = appProperties.getAdmin();

        if (!admin.isSeedEnabled()) {
            log.info("Bỏ qua seed tài khoản admin (ADMIN_SEED_ENABLED=false)");
            return;
        }
        if (admin.getPassword() == null || admin.getPassword().isBlank()) {
            log.warn("Bỏ qua seed tài khoản admin: ADMIN_PASSWORD rỗng (không tạo tài khoản mật khẩu rỗng)");
            return;
        }
        if (userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(admin.getEmail())) {
            log.info("Tài khoản admin {} đã tồn tại, bỏ qua seed", admin.getEmail());
            return;
        }

        Role adminRole = roleRepository.findByCodeAndDeletedAtIsNull(RoleCode.ADMIN)
                .orElseThrow(() -> new IllegalStateException(
                        "Chưa seed vai trò " + RoleCode.ADMIN + " (kiểm tra V2 migration)"));

        User user = User.builder()
                .fullName(admin.getFullName())
                .email(admin.getEmail().toLowerCase())
                .phone(admin.getPhone())
                .passwordHash(passwordEncoder.encode(admin.getPassword()))
                .gender(Gender.UNKNOWN)
                .status(UserStatus.ACTIVE)
                .emailVerifiedAt(Instant.now())
                .role(adminRole)
                .failedLoginCount(0)
                .build();
        userRepository.save(user);

        log.info("Đã tạo tài khoản quản trị khởi tạo: {}", admin.getEmail());
    }
}
