package com.webtro.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Cấu hình CORS. Bind từ {@code app.cors.*}. Origin cho phép đọc từ biến môi trường
 * {@code CORS_ALLOWED_ORIGINS} — không hardcode.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    private List<String> allowedOrigins = List.of("http://localhost:5173");
    private List<String> allowedMethods = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    private List<String> allowedHeaders = List.of("*");
    private List<String> exposedHeaders = List.of("Location", "Retry-After", "X-Trace-Id");
    private boolean allowCredentials = true;
    private long maxAge = 3600L;
}
