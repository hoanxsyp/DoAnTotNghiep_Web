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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Xử lý khi request ĐÃ xác thực nhưng THIẾU quyền → trả 403 theo envelope thống nhất
 * (canonical mục 7.1).
 */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiResponse<Void> body = ApiResponse.<Void>builder()
                .success(false)
                .message("Bạn không có quyền thực hiện thao tác này")
                .errorCode(ErrorCode.FORBIDDEN.name())
                .path(request.getRequestURI())
                .traceId(MDC.get(AppConstant.TRACE_ID_MDC_KEY))
                .build();

        objectMapper.writeValue(response.getWriter(), body);
    }
}
