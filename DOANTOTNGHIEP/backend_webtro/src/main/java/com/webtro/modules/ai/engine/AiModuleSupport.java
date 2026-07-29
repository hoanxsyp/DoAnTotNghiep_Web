package com.webtro.modules.ai.engine;

import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessException;
import com.webtro.modules.admin.service.SystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Hạ tầng dùng chung cho 4 module AI (canonical mục 10, §10.10): kiểm tra bật/tắt và chạy engine
 * có <b>timeout</b> trên {@code aiTaskExecutor}.
 *
 * <p>Bật/tắt: {@code ai.<module>.enabled = false} → ném {@link ErrorCode#AI_MODULE_DISABLED} (503).
 * Timeout: quá {@code ai.<module>.timeout_ms} → {@link ErrorCode#AI_SERVICE_UNAVAILABLE} (503).
 * AI hỏng KHÔNG được làm sập nghiệp vụ lõi (nguyên tắc vàng mục 7.0) — nơi gọi quyết định nuốt lỗi
 * (listener sentiment) hay trả 503 (endpoint chẩn đoán).
 */
@Slf4j
@Component
public class AiModuleSupport {

    private final SystemConfigService configService;
    private final Executor aiTaskExecutor;

    public AiModuleSupport(SystemConfigService configService,
                           @Qualifier("aiTaskExecutor") Executor aiTaskExecutor) {
        this.configService = configService;
        this.aiTaskExecutor = aiTaskExecutor;
    }

    /** Ném 503 {@code AI_MODULE_DISABLED} nếu module bị tắt qua cấu hình. */
    public void ensureEnabled(String enabledConfigKey) {
        if (!configService.getBoolean(enabledConfigKey)) {
            throw new BusinessException(ErrorCode.AI_MODULE_DISABLED);
        }
    }

    public boolean isEnabled(String enabledConfigKey) {
        return configService.getBoolean(enabledConfigKey);
    }

    /**
     * Chạy tác vụ engine với giới hạn thời gian đọc từ {@code timeoutConfigKey}. Quá hạn hoặc lỗi
     * → {@link ErrorCode#AI_SERVICE_UNAVAILABLE}.
     */
    public <T> T runWithTimeout(String moduleLabel, String timeoutConfigKey, Supplier<T> task) {
        int timeoutMs = configService.getInt(timeoutConfigKey);
        try {
            return CompletableFuture.supplyAsync(task, aiTaskExecutor)
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("AI [{}] vượt timeout {}ms", moduleLabel, timeoutMs);
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE,
                    "Dịch vụ AI (" + moduleLabel + ") phản hồi quá chậm, vui lòng thử lại sau");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        } catch (Exception e) {
            if (e.getCause() instanceof BusinessException be) {
                throw be;
            }
            log.error("AI [{}] lỗi khi xử lý: {}", moduleLabel, e.getMessage(), e);
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }
}
