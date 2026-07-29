package com.webtro.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Bộ mã hóa mật khẩu: BCrypt cost 12 (canonical mục 8, §11.1).
 *
 * <p>Tách khỏi {@code SecurityConfig} để tránh phụ thuộc vòng: nhiều thành phần (đăng ký, đổi mật
 * khẩu, seed admin) cần {@link PasswordEncoder} mà không cần cả cấu hình security.
 */
@Configuration
public class PasswordConfig {

    /** Cost 12: đủ chậm để chống dò mật khẩu, vẫn chấp nhận được cho đăng nhập tương tác. */
    private static final int BCRYPT_STRENGTH = 12;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }
}
