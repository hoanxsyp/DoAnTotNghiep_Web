package com.webtro.config;

import com.webtro.security.SecurityUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;

import java.util.Optional;

/**
 * Cung cấp "người thực hiện" cho JPA Auditing → tự điền {@code created_by} / {@code updated_by}
 * (canonical mục 6.1). Lấy từ SecurityContext; nếu là thao tác hệ thống (job nền, chưa đăng nhập)
 * thì trả rỗng và cột để null.
 *
 * <p>{@code @EnableJpaAuditing(auditorAwareRef = "auditorProvider")} khai ở
 * {@code WebtroApplication}.
 */
@Configuration
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<Long> auditorProvider() {
        return () -> SecurityUtils.getCurrentUserId().or(Optional::empty);
    }
}
