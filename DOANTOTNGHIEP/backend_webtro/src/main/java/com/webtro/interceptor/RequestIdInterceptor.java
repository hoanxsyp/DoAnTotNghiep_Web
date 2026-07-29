package com.webtro.interceptor;

import com.webtro.constant.AppConstant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * Gắn một mã truy vết (traceId) cho mỗi request vào MDC để mọi dòng log của request có cùng id,
 * và trả lại qua header {@code X-Trace-Id} (canonical mục 10 - correlation id). Nhờ đó khi người
 * dùng báo lỗi kèm traceId, ta tra được đúng chuỗi log.
 */
@Component
public class RequestIdInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        String incoming = request.getHeader(AppConstant.TRACE_ID_HEADER);
        String traceId = (incoming != null && !incoming.isBlank())
                ? incoming
                : UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MDC.put(AppConstant.TRACE_ID_MDC_KEY, traceId);
        response.setHeader(AppConstant.TRACE_ID_HEADER, traceId);
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                @NonNull Object handler, Exception ex) {
        // Dọn MDC để thread (được tái sử dụng trong pool) không mang traceId của request cũ.
        MDC.remove(AppConstant.TRACE_ID_MDC_KEY);
    }
}
