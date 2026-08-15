package com.webtro.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
/**
 * Lọc mọi request: nếu có {@code Authorization: Bearer <token>} hợp lệ (và không nằm trong
 * blacklist), dựng {@code Authentication} từ claims và đặt vào SecurityContext.
 *
 * <p>Dùng thẳng role trong token để tạo authority (không truy DB mỗi request → nhanh). Trạng
 * thái tài khoản bị khóa sau khi token phát hành sẽ bị chặn khi token hết hạn (≤15 phút) hoặc khi
 * làm mới token — đánh đổi hợp lý cho hiệu năng. Thao tác nhạy cảm vẫn kiểm tra lại ở service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final TokenBlacklistService blacklistService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Claims claims = jwtService.parseQuietly(token);
            if (claims != null && !blacklistService.isBlacklisted(jwtService.extractJti(claims))) {
                authenticate(claims, request);
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(Claims claims, HttpServletRequest request) {
        Long userId = jwtService.extractUserId(claims);
        String email = jwtService.extractEmail(claims);
        String role = jwtService.extractRole(claims);

        CustomUserDetails principal = new CustomUserDetails(
                userId, email, null, com.webtro.common.enums.UserStatus.ACTIVE, role);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }
}
