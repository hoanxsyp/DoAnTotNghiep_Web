package com.webtro.modules.admin.adapter;

import com.webtro.common.enums.AuditAction;
import com.webtro.modules.admin.service.AuditLogService;
import com.webtro.modules.payment.spi.AuditGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter (canonical luật 4) — hiện thực SPI {@link AuditGateway} của module {@code payment} bằng
 * {@link AuditLogService} của module {@code admin}. Dùng cho audit đổi gói/coupon
 * ({@code PACKAGE_CHANGE}) và hoàn tiền thủ công ({@code PAYMENT_REFUND}) [§11.4].
 *
 * <p>Interface trùng tên nhưng khác package với {@code moderation.spi.AuditGateway}; đây là adapter
 * riêng cho cổng của module {@code payment}.
 */
@Component
@RequiredArgsConstructor
public class PaymentAuditGatewayAdapter implements AuditGateway {

    private final AuditLogService auditLogService;

    /**
     * Ghi một bản ghi audit cho thao tác thanh toán. Bắc cầu sang
     * {@link AuditLogService#recordChange} (giữ được {@code targetLabel}); không kèm old/new value.
     */
    @Override
    @Transactional
    public Long record(AuditAction action, Long actorId, String targetType, Long targetId,
                       String targetLabel, String reason) {
        return auditLogService.recordChange(action, actorId, targetType, targetId,
                targetLabel, null, null, reason);
    }
}
