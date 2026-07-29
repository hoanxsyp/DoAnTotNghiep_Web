package com.webtro.modules.payment.spi;

import com.webtro.common.enums.AuditAction;

/**
 * Cổng (SPI) mà module {@code payment} cần từ module {@code admin} để ghi nhật ký kiểm toán
 * {@code audit_logs} {@code [§11.4]} — canonical luật 4.
 *
 * <p><b>Quyết định kiến trúc:</b> giống {@code moderation.spi.AuditGateway}, hợp đồng ghi audit
 * được khai báo dạng SPI <i>do bên tiêu dùng sở hữu</i>. Module {@code admin} cung cấp một
 * {@code @Component} hiện thực (bọc {@code AuditLogRepository}); cùng một lớp có thể hiện thực nhiều
 * SPI audit của các module khác nhau.
 *
 * <p>{@code [§11.4]} yêu cầu ghi audit cho: thay đổi gói dịch vụ / coupon
 * ({@link AuditAction#PACKAGE_CHANGE}) và hoàn tiền thủ công ({@link AuditAction#PAYMENT_REFUND}).
 */
public interface AuditGateway {

    /**
     * Ghi một bản ghi audit và trả về id.
     *
     * @param action      loại hành động (canonical §5 {@code AuditAction})
     * @param actorId     người thực hiện (null nếu hệ thống)
     * @param targetType  loại đối tượng ("PACKAGE", "COUPON", "PAYMENT")
     * @param targetId    id đối tượng bị tác động
     * @param targetLabel nhãn mô tả đối tượng (snapshot hiển thị)
     * @param reason      lý do/ghi chú
     * @return id bản ghi audit vừa tạo
     */
    Long record(AuditAction action, Long actorId, String targetType, Long targetId,
                String targetLabel, String reason);
}
