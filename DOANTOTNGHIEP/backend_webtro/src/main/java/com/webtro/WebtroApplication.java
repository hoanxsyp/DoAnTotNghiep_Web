package com.webtro;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

/**
 * Điểm khởi động của hệ thống "Website quảng cáo và tìm kiếm phòng trọ".
 *
 * <p>Kiến trúc: modular monolith — các module nghiệp vụ nằm dưới {@code com.webtro.modules},
 * dùng chung hạ tầng ở {@code common}, {@code config}, {@code security}. Xem
 * {@code docs/01_KIEN_TRUC_HE_THONG.md}.
 *
 * <p>Toàn hệ thống làm việc ở <b>UTC</b>. Quy đổi sang giờ Việt Nam là việc của tầng hiển thị
 * (frontend), không phải của backend — nếu backend tự quy đổi thì dữ liệu sẽ lệch khi triển khai
 * trên máy chủ có timezone khác.
 */
@SpringBootApplication
@ConfigurationPropertiesScan("com.webtro.config.properties")
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
@EnableCaching
@EnableAsync
@EnableScheduling
public class WebtroApplication {

    public static void main(String[] args) {
        // Phải đặt TRƯỚC khi Spring khởi tạo datasource/Hibernate, nếu không JDBC driver đã
        // đọc default timezone của JVM mất rồi.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(WebtroApplication.class, args);
    }
}
