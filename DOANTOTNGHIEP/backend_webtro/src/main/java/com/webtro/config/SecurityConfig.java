package com.webtro.config;

import com.webtro.security.CustomUserDetailsService;
import com.webtro.security.JwtAuthenticationEntryPoint;
import com.webtro.security.JwtAuthenticationFilter;
import com.webtro.security.RestAccessDeniedHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Cấu hình bảo mật trung tâm (canonical mục 8, §11.2).
 *
 * <p><b>{@code csrf().disable()} là ĐÚNG</b> với API stateless dùng Bearer token: hệ thống KHÔNG
 * dùng cookie cho bất kỳ mục đích xác thực nào (access token và refresh token đều do client giữ
 * trong {@code localStorage} và gửi tường minh qua header/body). Không có thông tin xác thực nào
 * được trình duyệt tự đính kèm ⇒ không có bề mặt CSRF (canonical mục 8).
 *
 * <p>{@code @EnableMethodSecurity} bật {@code @PreAuthorize} để kiểm quyền ở tầng service/controller
 * bằng permission code (RBAC 2 tầng).
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final CustomUserDetailsService userDetailsService;
    private final UrlBasedCorsConfigurationSource corsConfigurationSource;
    private final PasswordEncoder passwordEncoder;

    /** Các đường dẫn công khai — không cần đăng nhập (canonical mục 12). */
    private static final String[] PUBLIC_GET = {
            "/api/listings/**",
            "/api/search/**",
            "/api/categories/**",
            "/api/provinces/**",
            "/api/districts/**",
            "/api/wards/**",
            "/api/amenities/**",
            "/api/promotion-packages/**",
            "/api/users/*/public",
            "/api/users/*/listings",
            "/api/files/**",
            "/api/content/**",
    };

    private static final String[] PUBLIC_ANY = {
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/auth/verify-email",
            "/api/auth/resend-verification",
            "/api/ai/chatbot/**",
            "/api/payments/callback",
            "/actuator/health/**",
            "/actuator/info",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/ws/**",
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                // API stateless + Bearer token: không dùng cookie session -> tắt CSRF là đúng.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_ANY).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
                        // Mọi endpoint quản trị bắt buộc đăng nhập; quyền chi tiết do @PreAuthorize.
                        .requestMatchers("/api/admin/**").authenticated()
                        .anyRequest().authenticated())
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        // Dùng CHÍNH bean BCrypt cost 12 ở PasswordConfig — nếu dùng delegating encoder thì hash
        // BCrypt thuần (không tiền tố {bcrypt}) sẽ không khớp và đăng nhập luôn thất bại.
        provider.setPasswordEncoder(passwordEncoder);
        provider.setHideUserNotFoundExceptions(true);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
