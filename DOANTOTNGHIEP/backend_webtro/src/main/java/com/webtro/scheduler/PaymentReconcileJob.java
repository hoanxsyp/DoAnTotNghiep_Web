package com.webtro.scheduler;

import com.webtro.common.enums.NotificationType;
import com.webtro.common.enums.PaymentStatus;
import com.webtro.config.properties.AppProperties;
import com.webtro.constant.ConfigKey;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.payment.entity.Payment;
import com.webtro.modules.payment.repository.PaymentRepository;
import com.webtro.modules.notification.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Job 8 (canonical mục 11, §3.14) — <b>PaymentReconcileJob</b>, chạy mỗi 15 phút.
 *
 * <p>Chuyển các giao dịch còn {@code PENDING} quá {@link ConfigKey#PAYMENT_ORDER_EXPIRY_MINUTES}
 * (tính từ {@code created_at}) sang {@code FAILED} và báo cho người thanh toán
 * ({@link NotificationType#PAYMENT_FAILED}).
 *
 * <p>Idempotent (chỉ giao dịch còn PENDING mới bị đổi), lịch UTC, mỗi giao dịch xử lý trong
 * transaction riêng + try/catch, kết thúc log INFO.
 */
@Slf4j
@Component
public class PaymentReconcileJob {

    private static final int MAX_PER_RUN = 2000;

    private final PaymentRepository paymentRepository;
    private final NotificationService notificationService;
    private final SystemConfigService config;
    private final AppProperties appProperties;
    private final TransactionTemplate txTemplate;

    public PaymentReconcileJob(PaymentRepository paymentRepository,
                               NotificationService notificationService,
                               SystemConfigService config,
                               AppProperties appProperties,
                               PlatformTransactionManager txManager) {
        this.paymentRepository = paymentRepository;
        this.notificationService = notificationService;
        this.config = config;
        this.appProperties = appProperties;
        this.txTemplate = new TransactionTemplate(txManager);
    }

    @Scheduled(cron = "0 */15 * * * *", zone = "UTC")
    public void run() {
        if (!appProperties.getScheduler().isEnabled()) {
            return;
        }
        int expiryMinutes = config.getInt(ConfigKey.PAYMENT_ORDER_EXPIRY_MINUTES);
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(expiryMinutes));

        List<Payment> stale = paymentRepository.findStalePendingPayments(cutoff, PageRequest.of(0, MAX_PER_RUN));

        int failed = 0;
        int skipped = 0;
        int errors = 0;
        for (Payment payment : stale) {
            Long id = payment.getId();
            Long userId = payment.getUserId();
            try {
                boolean changed = Boolean.TRUE.equals(txTemplate.execute(status -> failOne(id, expiryMinutes)));
                if (changed) {
                    failed++;
                    notifyUserSafe(userId, id);
                } else {
                    skipped++;
                }
            } catch (RuntimeException e) {
                errors++;
                log.error("PaymentReconcileJob: lỗi xử lý giao dịch quá hạn id={}: {}", id, e.getMessage(), e);
            }
        }
        log.info("PaymentReconcileJob hoàn tất: hạn={} phút, ứng viên={}, chuyển FAILED={}, bỏ qua={}, lỗi={}",
                expiryMinutes, stale.size(), failed, skipped, errors);
    }

    private boolean failOne(Long id, int expiryMinutes) {
        Payment payment = paymentRepository.findByIdAndDeletedAtIsNull(id).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.PENDING) {
            return false; // đã được thanh toán/hủy bởi luồng khác — idempotent
        }
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason("Tự động hủy: đơn quá hạn thanh toán (" + expiryMinutes + " phút).");
        paymentRepository.save(payment);
        return true;
    }

    private void notifyUserSafe(Long userId, Long paymentId) {
        if (userId == null) {
            return;
        }
        try {
            notificationService.notifyUser(userId, NotificationType.PAYMENT_FAILED,
                    NotificationType.PAYMENT_FAILED.getLabel(),
                    "Giao dịch #" + paymentId + " đã hết hạn thanh toán và bị hủy tự động. "
                            + "Vui lòng tạo lại nếu bạn vẫn muốn mua gói.");
        } catch (RuntimeException e) {
            log.error("PaymentReconcileJob: gửi thông báo thất bại cho user={} (payment id={}) lỗi: {}",
                    userId, paymentId, e.getMessage(), e);
        }
    }
}
