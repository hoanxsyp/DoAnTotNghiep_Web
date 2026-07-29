package com.webtro.security;

import com.webtro.config.properties.AppProperties;
import com.webtro.constant.AppConstant;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.RateLimitException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Giới hạn tần suất trên Redis ({@code INCR} + {@code EXPIRE}) — canonical mục 8 (§11.10).
 * Tự viết, không dùng bucket4j (theo canonical mục 1.1).
 *
 * <p><b>Fail-open</b> (canonical mục 8): nếu Redis chết, cho request đi qua (log WARN). Giới hạn
 * tần suất là lớp chống lạm dụng, không được biến sự cố hạ tầng thành sập toàn site.
 *
 * <p>Ngưỡng cụ thể (số lần / cửa sổ) đọc từ {@code system_configs} tại nơi gọi, truyền vào
 * {@link #checkLimit}. Lớp này chỉ lo cơ chế đếm.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;
    private final AppProperties appProperties;

    /**
     * Tăng bộ đếm cho {@code action:identifier} và ném {@link RateLimitException} nếu vượt
     * {@code maxRequests} trong {@code window}.
     *
     * @param action     tên hành động (ví dụ "login", "comment")
     * @param identifier khóa phân biệt (userId, IP, hoặc IP+email)
     * @param maxRequests số lần tối đa
     * @param window     độ dài cửa sổ
     */
    public void checkLimit(String action, String identifier, int maxRequests, Duration window) {
        if (!appProperties.getRateLimit().isEnabled()) {
            return;
        }
        String key = AppConstant.REDIS_RATE_LIMIT_PREFIX + action + ":" + identifier;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                // Lần đầu trong cửa sổ: đặt hạn hết hạn.
                redisTemplate.expire(key, window);
            }
            if (count != null && count > maxRequests) {
                Long ttl = redisTemplate.getExpire(key);
                long retryAfter = ttl != null && ttl > 0 ? ttl : window.getSeconds();
                throw new RateLimitException(ErrorCode.RATE_LIMIT_EXCEEDED,
                        "Bạn thao tác quá nhanh, vui lòng thử lại sau " + retryAfter + " giây", retryAfter);
            }
        } catch (RateLimitException e) {
            throw e;
        } catch (RuntimeException e) {
            // Fail-open: Redis lỗi -> cho qua.
            log.warn("Redis lỗi khi rate-limit (fail-open, cho qua) action={} id={}: {}",
                    action, identifier, e.getMessage());
        }
    }

    /** Đếm hiện tại (không tăng) — dùng để quyết định hiện captcha sau N lần sai. */
    public long currentCount(String action, String identifier) {
        String key = AppConstant.REDIS_RATE_LIMIT_PREFIX + action + ":" + identifier;
        try {
            String v = redisTemplate.opsForValue().get(key);
            return v == null ? 0 : Long.parseLong(v);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Xóa bộ đếm (ví dụ khi đăng nhập thành công thì reset đếm sai). */
    public void reset(String action, String identifier) {
        try {
            redisTemplate.delete(AppConstant.REDIS_RATE_LIMIT_PREFIX + action + ":" + identifier);
        } catch (RuntimeException e) {
            log.warn("Không reset được rate-limit key: {}", e.getMessage());
        }
    }
}
