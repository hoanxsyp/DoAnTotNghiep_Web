package com.webtro.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * Tinh chỉnh Jackson: thời gian dạng ISO-8601 UTC (canonical mục 7.3), không serialize ngày dưới
 * dạng timestamp số. Phần lớn cấu hình đã ở application.yml; đây là phần cần code.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        return builder -> {
            builder.modules(new JavaTimeModule());
            builder.timeZone(TimeZone.getTimeZone("UTC"));
            builder.featuresToDisable(
                    com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        };
    }

    /**
     * ObjectMapper chính (được các thành phần như entry point, filter tự tiêm). Spring Boot đã
     * cấu hình sẵn qua builder ở trên; khai bean tường minh để inject rõ ràng.
     */
    @Bean
    public ObjectMapper objectMapper(
            org.springframework.http.converter.json.Jackson2ObjectMapperBuilder builder) {
        return builder.build();
    }
}
