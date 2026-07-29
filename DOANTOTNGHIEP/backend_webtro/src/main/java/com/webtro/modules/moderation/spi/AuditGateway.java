package com.webtro.modules.moderation.spi;

import com.webtro.common.enums.AuditAction;

/**
 * Cổng (SPI) mà module {@code moderation} cần từ module {@code admin} để ghi nhật ký kiểm toán
 * {@code audit_logs} {@code [§11.4]} — canonical luật 4. Module {@code admin} phải cung cấp một
 * {@code @Component} hiện thực interface này (bọc {@code AuditLogRepository}).
 *
 * <p>{@code [§3.13]} <i>"Tất cả thao tác xử lý report cần có log"</i>: mọi lần nhận xử lý, kết
 * luận report, gửi cảnh báo, khóa/ẩn tin đều ghi một dòng audit.
 */
public interface AuditGateway {

    /**
     * Ghi một bản ghi audit và trả về id.
     *
     * @param action      loại hành động (canonical §5 {@code AuditAction})
     * @param actorId     người thực hiện (null nếu hệ thống)
     * @param targetType  loại đối tượng bị tác động ("LISTING", "USER", "COMMENT", "REVIEW", "REPORT")
     * @param targetId    id đối tượng bị tác động
     * @param targetLabel nhãn mô tả đối tượng (snapshot hiển thị)
     * @param reason      lý do/ghi chú
     * @return id bản ghi audit vừa tạo
     */
    Long record(AuditAction action, Long actorId, String targetType, Long targetId,
                String targetLabel, String reason);
}
