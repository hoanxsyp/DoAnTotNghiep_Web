package com.webtro.modules.admin.adapter;

import com.webtro.common.enums.AuditAction;
import com.webtro.modules.admin.service.AuditLogService;
import com.webtro.modules.moderation.spi.AuditGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter (canonical luật 4) — hiện thực SPI {@link AuditGateway} của module {@code moderation}
 * bằng {@link AuditLogService} của module {@code admin}. Module {@code moderation} chỉ khai báo cổng
 * ghi audit của riêng nó; lớp này lấp đầy cổng đó, không cho module khác chạm vào repository admin.
 */
@Component
@RequiredArgsConstructor
public class ModerationAuditGatewayAdapter implements AuditGateway {

    private final AuditLogService auditLogService;

    /**
     * Ghi một bản ghi audit cho thao tác kiểm duyệt. Bắc cầu sang
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
