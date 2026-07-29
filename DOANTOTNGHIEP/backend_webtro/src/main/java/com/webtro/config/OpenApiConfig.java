package com.webtro.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Cấu hình Swagger/OpenAPI (canonical mục 7.3). Khai báo security scheme {@code bearerAuth} để
 * nút "Authorize" trong Swagger UI cho phép dán JWT và thử các endpoint cần đăng nhập.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI webtroOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Webtro API")
                        .description("API hệ thống quảng cáo và tìm kiếm phòng trọ. "
                                + "Đăng nhập qua /api/auth/login để lấy access token, "
                                + "rồi bấm Authorize và dán token.")
                        .version("1.0.0")
                        .contact(new Contact().name("Webtro").email("support@webtro.local"))
                        .license(new License().name("Đồ án tốt nghiệp")))
                .servers(List.of(
                        new Server().url("/").description("Server hiện tại")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Dán access token JWT (không kèm chữ 'Bearer ')")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
