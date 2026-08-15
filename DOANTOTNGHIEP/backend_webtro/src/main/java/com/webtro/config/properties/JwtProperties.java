package com.webtro.config.properties;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Cấu hình JWT (canonical mục 8). Bind từ {@code app.jwt.*}.
 *
 * <p><b>Fail-fast:</b> nếu {@code JWT_SECRET} rỗng hoặc ngắn hơn 32 byte, ứng dụng KHÔNG khởi
 * động được. Đây là chủ ý: một khóa ký yếu hoặc rỗng còn tệ hơn là dừng hẳn — nó tạo cảm giác an
 * toàn giả trong khi mọi token đều có thể bị giả mạo.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** Khóa bí mật ký HMAC. Bắt buộc, tối thiểu 32 byte. Không có giá trị mặc định. */
    @NotBlank(message = "JWT_SECRET là bắt buộc")
    private String secret;

    @NotBlank
    private String issuer = "webtro-api";

    @NotNull
    private Duration accessTokenTtl = Duration.ofMinutes(15);

    @NotNull
    private Duration refreshTokenTtl = Duration.ofDays(1);

    @PostConstruct
    void validate() {
        int len = secret == null ? 0 : secret.getBytes(StandardCharsets.UTF_8).length;
        if (len < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET phải dài tối thiểu 32 byte (hiện tại: " + len + " byte). "
                            + "Sinh khóa mới bằng: openssl rand -base64 48");
        }
    }
}
