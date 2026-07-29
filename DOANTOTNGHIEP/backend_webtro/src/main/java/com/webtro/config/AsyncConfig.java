package com.webtro.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Executor bất đồng bộ cho AI và email (canonical mục 10, §11.6). AI chạy async qua queue để
 * không chặn request người dùng; nếu queue đầy thì {@code CallerRunsPolicy} khiến tác vụ chạy
 * ngay trên thread gọi (tự giảm tải) thay vì mất tác vụ.
 */
@Slf4j
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    /**
     * Executor cho các tác vụ AI (phân tích cảm xúc, dự đoán giá, tính gợi ý).
     * Đây là executor được {@code @Async("aiTaskExecutor")} tham chiếu (canonical mục 10.1).
     */
    @Bean("aiTaskExecutor")
    public Executor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("webtro-ai-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /** Executor cho gửi email — tách riêng để mail chậm không chiếm slot của AI. */
    @Bean("mailTaskExecutor")
    public Executor mailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("webtro-mail-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /** Executor mặc định cho {@code @Async} không chỉ tên. */
    @Override
    public Executor getAsyncExecutor() {
        return aiTaskExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        // Lỗi trong tác vụ @Async void không được nuốt im lặng (canonical luật job).
        return new LoggingAsyncExceptionHandler();
    }

    private static class LoggingAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {
        private final AsyncUncaughtExceptionHandler delegate = new SimpleAsyncUncaughtExceptionHandler();

        @Override
        public void handleUncaughtException(Throwable ex, java.lang.reflect.Method method, Object... params) {
            log.error("Lỗi trong tác vụ async {}: {}", method.getName(), ex.getMessage(), ex);
            delegate.handleUncaughtException(ex, method, params);
        }
    }
}
