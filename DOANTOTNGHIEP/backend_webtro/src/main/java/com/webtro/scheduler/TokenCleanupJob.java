package com.webtro.scheduler;

import com.webtro.config.properties.AppProperties;
import com.webtro.modules.user.repository.PasswordResetTokenRepository;
import com.webtro.modules.user.repository.RefreshTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

/**
 * Job 7 (canonical mục 11) — <b>TokenCleanupJob</b>, chạy 03:00 UTC hằng ngày.
 *
 * <p>Xóa CỨNG các {@code refresh_tokens} và {@code password_reset_tokens} đã hết hạn (không còn giá
 * trị bảo mật/nghiệp vụ). Mỗi loại xóa trong transaction riêng để lỗi ở loại này không chặn loại kia.
 *
 * <p>Idempotent (chạy lại chỉ xóa nốt phần còn hết hạn), lịch UTC, kết thúc log INFO kèm số liệu.
 */
@Slf4j
@Component
public class TokenCleanupJob {

    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final AppProperties appProperties;
    private final TransactionTemplate txTemplate;

    public TokenCleanupJob(RefreshTokenRepository refreshTokenRepository,
                           PasswordResetTokenRepository passwordResetTokenRepository,
                           AppProperties appProperties,
                           PlatformTransactionManager txManager) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.appProperties = appProperties;
        this.txTemplate = new TransactionTemplate(txManager);
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    public void run() {
        if (!appProperties.getScheduler().isEnabled()) {
            return;
        }
        Instant now = Instant.now();

        int refreshDeleted = 0;
        try {
            Integer n = txTemplate.execute(status -> refreshTokenRepository.deleteExpiredBefore(now));
            refreshDeleted = n == null ? 0 : n;
        } catch (RuntimeException e) {
            log.error("TokenCleanupJob: lỗi xóa refresh token hết hạn: {}", e.getMessage(), e);
        }

        int resetDeleted = 0;
        try {
            Integer n = txTemplate.execute(status -> passwordResetTokenRepository.deleteExpiredBefore(now));
            resetDeleted = n == null ? 0 : n;
        } catch (RuntimeException e) {
            log.error("TokenCleanupJob: lỗi xóa password reset token hết hạn: {}", e.getMessage(), e);
        }

        log.info("TokenCleanupJob hoàn tất: refresh_tokens đã xóa={}, password_reset_tokens đã xóa={}",
                refreshDeleted, resetDeleted);
    }
}
