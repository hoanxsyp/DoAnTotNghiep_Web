package com.webtro.security;

import com.webtro.config.properties.JwtProperties;
import com.webtro.constant.AppConstant;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phát hành và xác thực JWT access token (canonical mục 8).
 *
 * <p>Access token: 15 phút, chứa {@code sub} (userId), {@code email}, {@code roles[]},
 * {@code permissions[]}, {@code jti}. Refresh token KHÔNG do lớp này quản (nó là opaque UUID lưu
 * DB, xem module auth) — lớp này chỉ lo access token JWT.
 *
 * <p>Lớp này cố ý KHÔNG phụ thuộc entity User: nó nhận các giá trị nguyên thủy để tránh kéo module
 * user vào tầng security.
 */
@Slf4j
@Service
public class JwtService {

    private final JwtProperties props;
    private final SecretKey key;

    public JwtService(JwtProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Phát hành access token. {@code jti} là định danh duy nhất để có thể đưa vào blacklist khi
     * logout (canonical mục 8).
     */
    public String generateAccessToken(Long userId, String email, List<String> roles, List<String> permissions) {
        Instant now = Instant.now();
        Instant expiry = now.plus(props.getAccessTokenTtl());
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .issuer(props.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim(AppConstant.JWT_CLAIM_EMAIL, email)
                .claim(AppConstant.JWT_CLAIM_ROLES, roles)
                .claim(AppConstant.JWT_CLAIM_PERMISSIONS, permissions)
                .signWith(key)
                .compact();
    }

    /**
     * Phân tích và xác thực token. Ném {@link JwtException} nếu chữ ký sai hoặc hết hạn.
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(props.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Trả về claims nếu token hợp lệ, {@code null} nếu không (không ném ngoại lệ). */
    public Claims parseQuietly(String token) {
        try {
            return parse(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token không hợp lệ: {}", e.getMessage());
            return null;
        }
    }

    public Long extractUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public String extractJti(Claims claims) {
        return claims.getId();
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(Claims claims) {
        Object roles = claims.get(AppConstant.JWT_CLAIM_ROLES);
        return roles instanceof List<?> list ? (List<String>) list : List.of();
    }

    @SuppressWarnings("unchecked")
    public List<String> extractPermissions(Claims claims) {
        Object perms = claims.get(AppConstant.JWT_CLAIM_PERMISSIONS);
        return perms instanceof List<?> list ? (List<String>) list : List.of();
    }

    public String extractEmail(Claims claims) {
        return claims.get(AppConstant.JWT_CLAIM_EMAIL, String.class);
    }

    /** Số giây còn lại tới khi token hết hạn — dùng đặt TTL cho blacklist Redis khi logout. */
    public long secondsUntilExpiry(Claims claims) {
        long remaining = claims.getExpiration().toInstant().getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0, remaining);
    }

    public Map<String, Object> describe(Claims claims) {
        return Map.of(
                "userId", claims.getSubject(),
                "jti", claims.getId(),
                "expiresAt", claims.getExpiration().toInstant().toString());
    }
}
