package com.webtro.modules.admin.service;

import com.webtro.common.PageResponse;
import com.webtro.common.enums.AuditAction;
import com.webtro.common.enums.AuditActorType;
import com.webtro.modules.admin.dto.response.AuditLogResponse;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

/**
 * Ghi và tra cứu nhật ký kiểm toán {@code audit_logs} (canonical §11.4) — dịch vụ trung tâm mà MỌI
 * module gọi khi thực hiện thao tác quan trọng (khóa user, đổi vai trò, duyệt/khóa tin, đổi cấu
 * hình AI/hệ thống, hoàn tiền...).
 *
 * <p>Bản ghi là <b>append-only</b>: không sửa, không xóa — bảo toàn tính toàn vẹn kiểm toán. Vì các
 * module tầng dưới (payment, moderation) khai báo SPI {@code AuditGateway} của riêng chúng, lớp hiện
 * thực service này đồng thời hiện thực các SPI đó (một @Component, nhiều interface — canonical luật
 * 4).
 */
public interface AuditLogService {

    /**
     * Ghi một bản ghi audit tối giản (không kèm old/new value).
     *
     * @param action     loại hành động (canonical §5)
     * @param actorType  người dùng hay hệ thống thực hiện
     * @param actorId    users.id của actor (null nếu là SYSTEM hoặc đã bị xóa)
     * @param targetType loại đối tượng bị tác động ("USER", "LISTING", "CONFIG", "PACKAGE"...)
     * @param targetId   id đối tượng bị tác động (có thể null với thao tác cấu hình)
     * @param detail     lý do/ghi chú
     * @return id bản ghi audit vừa tạo
     */
    Long record(AuditAction action, AuditActorType actorType, Long actorId,
                String targetType, Long targetId, String detail);

    /**
     * Ghi một bản ghi audit đầy đủ kèm giá trị trước/sau (cột JSON {@code old_value}/{@code new_value}).
     *
     * @param action      loại hành động
     * @param actorId     users.id của actor (null nếu hệ thống)
     * @param targetType  loại đối tượng
     * @param targetId    id đối tượng (nullable)
     * @param targetLabel nhãn hiển thị nhanh (snapshot)
     * @param oldValue    JSON giá trị trước (nullable)
     * @param newValue    JSON giá trị sau (nullable)
     * @param reason      lý do
     * @return id bản ghi audit vừa tạo
     */
    Long recordChange(AuditAction action, Long actorId, String targetType, Long targetId,
                      String targetLabel, String oldValue, String newValue, String reason);

    /**
     * Tra cứu nhật ký kiểm toán có lọc (canonical 4.20.3). Khoảng {@code from..to} tối đa 90 ngày,
     * vượt ném {@code AUDIT_LOG_RANGE_TOO_LARGE}.
     */
    PageResponse<AuditLogResponse> query(List<AuditAction> actions, Long actorId, String targetType,
                                         Long targetId, Instant from, Instant to, Pageable pageable);
}
