package com.webtro.security;

import com.webtro.constant.AppConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Danh sách đen JWT (canonical mục 8): khi logout, đưa {@code jti} của access token vào Redis với
 * TTL = hạn còn lại của token, để token đã đăng xuất không dùng lại được dù chưa hết hạn.
 *
 * <p><b>Fail-closed</b> (canonical mục 8): nếu Redis chết, {@link #isBlacklisted} trả {@code true}
 * (coi như đã thu hồi). Khác với cache (fail-open): ở đây lỗi hạ tầng KHÔNG được biến thành lỗ
 * hổng bảo mật — thà từ chối nhầm còn hơn chấp nhận token đã thu hồi.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final StringRedisTemplate redisTemplate;

    public void blacklist(String jti, long ttlSeconds) {
        if (jti == null || ttlSeconds <= 0) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key(jti), "1", Duration.ofSeconds(ttlSeconds));
        } catch (RuntimeException e) {
            // Không chặn logout nếu Redis lỗi, nhưng ghi lại để cảnh báo vận hành.
            log.error("Không đưa được token vào blacklist (jti={}): {}", jti, e.getMessage());
        }
    }

    public boolean isBlacklisted(String jti) {
        if (jti == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key(jti)));
        } catch (RuntimeException e) {
            // Fail-closed: Redis chết -> coi như token đã bị thu hồi.
            log.error("Redis lỗi khi kiểm tra blacklist (fail-closed, từ chối token): {}", e.getMessage());
            return true;
        }
    }

    private String key(String jti) {
        return AppConstant.REDIS_JWT_BLACKLIST_PREFIX + jti;
    }
}
