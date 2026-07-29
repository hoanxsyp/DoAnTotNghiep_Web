package com.webtro.modules.admin.service.impl;

import com.webtro.common.PageResponse;
import com.webtro.common.enums.AuditAction;
import com.webtro.common.enums.AuditActorType;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessException;
import com.webtro.modules.admin.dto.response.AuditLogResponse;
import com.webtro.modules.admin.entity.AuditLog;
import com.webtro.modules.admin.mapper.AuditLogMapper;
import com.webtro.modules.admin.repository.AuditLogRepository;
import com.webtro.modules.admin.service.AuditLogService;
import com.webtro.modules.admin.specification.AuditLogSpecifications;
import com.webtro.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Cài đặt {@link AuditLogService} — dịch vụ ghi/tra cứu nhật ký kiểm toán trung tâm.
 *
 * <p>Các SPI {@code AuditGateway} của module {@code payment} và {@code moderation} (trùng chữ ký,
 * khác package) được hiện thực bởi các adapter riêng trong {@code admin.adapter.*}, bắc cầu về
 * service này qua {@link #recordChange} — tránh việc một @Component đăng ký nhiều interface gây
 * mập mờ bean.
 *
 * <p>Bản ghi audit là append-only nên mọi method ghi đều nuốt lỗi phụ (ip/user-agent/request-id) an
 * toàn — không được để việc ghi audit làm hỏng giao dịch nghiệp vụ chính.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    /** Trần khoảng tra cứu audit: 90 ngày (canonical 4.20.3). */
    private static final Duration MAX_QUERY_RANGE = Duration.ofDays(90);

    private final AuditLogRepository auditLogRepository;
    private final AuditLogMapper auditLogMapper;

    // ============================ Ghi ============================

    @Override
    @Transactional
    public Long record(AuditAction action, AuditActorType actorType, Long actorId,
                       String targetType, Long targetId, String detail) {
        return persist(action, actorId, targetType, targetId, null, null, null, detail);
    }

    @Override
    @Transactional
    public Long recordChange(AuditAction action, Long actorId, String targetType, Long targetId,
                             String targetLabel, String oldValue, String newValue, String reason) {
        return persist(action, actorId, targetType, targetId, targetLabel, oldValue, newValue, reason);
    }

    // ============================ Đọc ============================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> query(List<AuditAction> actions, Long actorId,
                                                String targetType, Long targetId,
                                                Instant from, Instant to, Pageable pageable) {
        Instant now = Instant.now();
        Instant effectiveTo = to != null ? to : now;
        Instant effectiveFrom = from != null ? from : effectiveTo.minus(Duration.ofDays(30));
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new BusinessException(ErrorCode.STATISTIC_RANGE_INVALID,
                    "Ngày bắt đầu phải trước ngày kết thúc");
        }
        if (Duration.between(effectiveFrom, effectiveTo).compareTo(MAX_QUERY_RANGE) > 0) {
            throw new BusinessException(ErrorCode.AUDIT_LOG_RANGE_TOO_LARGE,
                    "Khoảng thời gian tra cứu tối đa 90 ngày");
        }
        Page<AuditLog> page = auditLogRepository.findAll(
                AuditLogSpecifications.filter(actions, actorId, targetType, targetId,
                        effectiveFrom, effectiveTo),
                pageable);
        return PageResponse.from(page, auditLogMapper::toResponse);
    }

    // ============================ Nội bộ ============================

    private Long persist(AuditAction action, Long actorId, String targetType, Long targetId,
                         String targetLabel, String oldValue, String newValue, String reason) {
        AuditLog entry = AuditLog.builder()
                .actorId(actorId)
                .actorEmail(resolveActorEmail(actorId))
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .targetLabel(truncate(targetLabel, 200))
                .oldValue(toJson(oldValue))
                .newValue(toJson(newValue))
                .reason(truncate(reason, 500))
                .ipAddress(currentIp())
                .userAgent(truncate(currentUserAgent(), 255))
                .requestId(currentRequestId())
                .build();
        AuditLog saved = auditLogRepository.save(entry);
        return saved.getId();
    }

    /**
     * Cột {@code old_value}/{@code new_value} là kiểu JSON của MySQL. Người gọi thường truyền một
     * chuỗi thô (ví dụ tên trạng thái "PENDING") — bản thân nó KHÔNG phải JSON hợp lệ nên
     * {@code CAST(? AS JSON)} sẽ lỗi "Invalid JSON text". Hàm này chuẩn hóa:
     * <ul>
     *   <li>null → null (bỏ qua)</li>
     *   <li>đã là JSON hợp lệ (bắt đầu bằng {@code { [ "} hoặc số/true/false/null) → giữ nguyên</li>
     *   <li>còn lại → bọc thành chuỗi JSON hợp lệ (thoát dấu nháy) </li>
     * </ul>
     */
    private String toJson(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        char first = trimmed.charAt(0);
        // Chỉ coi là JSON có sẵn khi là object/array/string JSON có cấu trúc rõ ràng. Chuỗi thô như
        // "PENDING" hay "3 ngày" đều được bọc thành JSON string cho an toàn (số cũng bọc thành chuỗi,
        // vẫn hợp lệ và đủ để hiển thị audit).
        if (first == '{' || first == '[' || first == '"') {
            return trimmed;
        }
        // Bọc thành JSON string: thoát \ và " rồi thêm nháy kép hai đầu.
        String escaped = trimmed.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }

    /** Email actor (snapshot): lấy từ principal đang đăng nhập nếu trùng actorId. */
    private String resolveActorEmail(Long actorId) {
        if (actorId == null) {
            return null;
        }
        return SecurityUtils.getCurrentUser()
                .filter(u -> actorId.equals(u.getId()))
                .map(u -> truncate(u.getEmail(), 190))
                .orElse(null);
    }

    private String currentIp() {
        HttpServletRequest req = currentRequest();
        if (req == null) {
            return null;
        }
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return truncate(forwarded.split(",")[0].trim(), 45);
        }
        return truncate(req.getRemoteAddr(), 45);
    }

    private String currentUserAgent() {
        HttpServletRequest req = currentRequest();
        return req != null ? req.getHeader("User-Agent") : null;
    }

    private String currentRequestId() {
        String fromMdc = MDC.get("traceId");
        if (fromMdc != null && !fromMdc.isBlank()) {
            return truncate(fromMdc, 36);
        }
        HttpServletRequest req = currentRequest();
        if (req != null) {
            String header = req.getHeader("X-Request-Id");
            if (header != null && !header.isBlank()) {
                return truncate(header, 36);
            }
        }
        return null;
    }

    private HttpServletRequest currentRequest() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                return attrs.getRequest();
            }
        } catch (Exception ex) {
            log.debug("Không lấy được HttpServletRequest cho audit: {}", ex.getMessage());
        }
        return null;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
