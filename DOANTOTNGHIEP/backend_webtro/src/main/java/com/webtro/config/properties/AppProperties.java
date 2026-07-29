package com.webtro.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Cấu hình cấp ứng dụng gộp: frontend, mail, payment, rate-limit toggle, admin seed, scheduler.
 * Bind từ {@code app.*}. Đây là cấu hình HẠ TẦNG/KỸ THUẬT — ngưỡng nghiệp vụ nằm ở
 * {@code system_configs}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Frontend frontend = new Frontend();
    private Mail mail = new Mail();
    private Payment payment = new Payment();
    private RateLimit rateLimit = new RateLimit();
    private Admin admin = new Admin();
    private Scheduler scheduler = new Scheduler();
    private Seed seed = new Seed();

    @Getter
    @Setter
    public static class Frontend {
        private String baseUrl = "http://localhost:5173";
        private String verifyEmailPath = "/xac-thuc-email";
        private String resetPasswordPath = "/dat-lai-mat-khau";
        private String listingDetailPath = "/tin";
    }

    @Getter
    @Setter
    public static class Mail {
        private boolean enabled = true;
        private String fromAddress = "no-reply@webtro.local";
        private String fromName = "Webtro";
    }

    @Getter
    @Setter
    public static class Payment {
        /** SANDBOX theo [§13.2]. */
        private String provider = "SANDBOX";
        private String returnUrl = "http://localhost:5173/quan-ly/thanh-toan/ket-qua";
        private String callbackSecret = "";
        private Duration pendingTimeout = Duration.ofMinutes(30);
    }

    @Getter
    @Setter
    public static class RateLimit {
        private boolean enabled = true;
    }

    @Getter
    @Setter
    public static class Admin {
        private boolean seedEnabled = true;
        private String email = "admin@webtro.local";
        private String password = "";
        private String fullName = "Quan tri he thong";
        private String phone = "0900000000";
    }

    @Getter
    @Setter
    public static class Scheduler {
        private boolean enabled = true;
    }

    @Getter
    @Setter
    public static class Seed {
        /**
         * Bật gieo dữ liệu mẫu THẬT vào DB lúc khởi động (người dùng, tin đăng, bình luận, đánh giá,
         * thanh toán...). Idempotent: chỉ chạy khi DB chưa có dữ liệu mẫu. Tắt ở môi trường thật.
         */
        private boolean demoEnabled = true;
    }
}
