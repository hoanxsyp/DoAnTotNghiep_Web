package com.webtro.config;

import com.webtro.constant.AppConstant;
import com.webtro.interceptor.RequestIdInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.data.web.SortHandlerMethodArgumentResolver;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Cấu hình Web MVC: đăng ký interceptor gắn traceId, và ép giới hạn kích thước trang
 * (canonical mục 7.3: size tối đa 100) để một request không thể yêu cầu trả về cả bảng.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RequestIdInterceptor requestIdInterceptor;

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(requestIdInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addArgumentResolvers(
            @NonNull List<org.springframework.web.method.support.HandlerMethodArgumentResolver> resolvers) {
        PageableHandlerMethodArgumentResolver pageableResolver =
                new PageableHandlerMethodArgumentResolver(new SortHandlerMethodArgumentResolver());
        pageableResolver.setMaxPageSize(AppConstant.MAX_PAGE_SIZE);
        pageableResolver.setFallbackPageable(
                org.springframework.data.domain.PageRequest.of(0, AppConstant.DEFAULT_PAGE_SIZE));
        pageableResolver.setOneIndexedParameters(false);
        resolvers.add(pageableResolver);
    }

    /** Dùng khi cần Pageable mặc định ngoài ngữ cảnh controller. */
    public static Pageable defaultPageable() {
        return org.springframework.data.domain.PageRequest.of(0, AppConstant.DEFAULT_PAGE_SIZE);
    }
}
