package com.webtro.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webtro.common.ApiResponse;
import com.webtro.constant.AppConstant;
import com.webtro.constant.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Xử lý khi request CHƯA xác thực chạm vào endpoint yêu cầu đăng nhập → trả 401 theo envelope
 * thống nhất (canonical mục 7.1), thay vì trang lỗi mặc định của Spring Security.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiResponse<Void> body = ApiResponse.<Void>builder()
                .success(false)
                .message("Bạn cần đăng nhập để tiếp tục")
                .errorCode(ErrorCode.UNAUTHORIZED.name())
                .path(request.getRequestURI())
                .traceId(MDC.get(AppConstant.TRACE_ID_MDC_KEY))
                .build();

        objectMapper.writeValue(response.getWriter(), body);
    }
}
