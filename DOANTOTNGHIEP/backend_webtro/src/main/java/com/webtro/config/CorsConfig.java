package com.webtro.config;

import com.webtro.config.properties.CorsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Nguồn cấu hình CORS dùng chung cho Spring Security. Origin cho phép đọc từ {@link CorsProperties}
 * (biến môi trường {@code CORS_ALLOWED_ORIGINS}) — không hardcode.
 */
@Configuration
@RequiredArgsConstructor
public class CorsConfig {

    private final CorsProperties props;

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.getAllowedOrigins());
        config.setAllowedMethods(props.getAllowedMethods());
        config.setAllowedHeaders(props.getAllowedHeaders());
        config.setExposedHeaders(props.getExposedHeaders());
        config.setAllowCredentials(props.isAllowCredentials());
        config.setMaxAge(props.getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
