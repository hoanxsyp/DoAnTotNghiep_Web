package com.webtro.modules.admin.service.impl;

import com.webtro.common.enums.AuditAction;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessException;
import com.webtro.modules.admin.dto.request.UpdateConfigRequest;
import com.webtro.modules.admin.dto.response.ConfigItemResponse;
import com.webtro.modules.admin.dto.response.SystemConfigResponse;
import com.webtro.modules.admin.dto.response.UpdateConfigResponse;
import com.webtro.modules.admin.entity.SystemConfig;
import com.webtro.modules.admin.mapper.SystemConfigMapper;
import com.webtro.modules.admin.repository.SystemConfigRepository;
import com.webtro.modules.admin.service.AdminSystemConfigService;
import com.webtro.modules.admin.service.AuditLogService;
import com.webtro.modules.admin.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cài đặt {@link AdminSystemConfigService}. Đọc trực tiếp {@link SystemConfigRepository} để dựng
 * danh sách nhóm; cập nhật ủy quyền {@link SystemConfigService#updateValue} (đã validate min/max +
 * evict cache) rồi ghi audit qua {@link AuditLogService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSystemConfigServiceImpl implements AdminSystemConfigService {

    /** Nhóm cấu hình do {@code /api/admin/ai/config} quản lý — loại khỏi endpoint này. */
    private static final String AI_PREFIX = "ai.";
    private static final String TRUST_PREFIX = "trust.";

    private static final Map<String, String> GROUP_LABELS = Map.ofEntries(
            Map.entry("LISTING", "Tin đăng"),
            Map.entry("MODERATION", "Kiểm duyệt"),
            Map.entry("INTERACTION", "Tương tác"),
            Map.entry("PROMOTION", "Gói dịch vụ"),
            Map.entry("SECURITY", "Bảo mật"),
            Map.entry("SPAM", "Chống spam & Rate limit"),
            Map.entry("UPLOAD", "Tải lên"),
            Map.entry("TRUST", "Điểm uy tín"),
            Map.entry("AI", "Trí tuệ nhân tạo"));

    private final SystemConfigRepository systemConfigRepository;
    private final SystemConfigService systemConfigService;
    private final SystemConfigMapper systemConfigMapper;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public SystemConfigResponse getConfigs(String group) {
        boolean all = group == null || group.isBlank() || "ALL".equalsIgnoreCase(group);
        List<SystemConfig> configs = systemConfigRepository
                .findByDeletedAtIsNullOrderByGroupNameAscDisplayOrderAsc();

        Map<String, List<ConfigItemResponse>> byGroup = new LinkedHashMap<>();
        for (SystemConfig c : configs) {
            String key = c.getConfigKey();
            if (key.startsWith(AI_PREFIX) || key.startsWith(TRUST_PREFIX)) {
                continue; // thuộc /api/admin/ai/config
            }
            if (!all && !group.equalsIgnoreCase(c.getGroupName())) {
                continue;
            }
            byGroup.computeIfAbsent(c.getGroupName(), k -> new ArrayList<>())
                    .add(systemConfigMapper.toResponse(c));
        }

        List<SystemConfigResponse.Group> groups = new ArrayList<>();
        for (Map.Entry<String, List<ConfigItemResponse>> e : byGroup.entrySet()) {
            groups.add(SystemConfigResponse.Group.builder()
                    .group(e.getKey())
                    .label(GROUP_LABELS.getOrDefault(e.getKey(), e.getKey()))
                    .configs(e.getValue())
                    .build());
        }

        return SystemConfigResponse.builder()
                .groups(groups)
                .note("Nhóm TRUST và AI xem/sửa tại /api/admin/ai/config (quyền AI_CONFIG_MANAGE)")
                .build();
    }

    @Override
    @Transactional
    public UpdateConfigResponse updateConfigs(UpdateConfigRequest request, Long actorId) {
        List<UpdateConfigResponse.UpdatedItem> updated = new ArrayList<>();
        List<Long> auditIds = new ArrayList<>();

        for (UpdateConfigRequest.ConfigEntry entry : request.getConfigs()) {
            String key = entry.getKey().trim();

            if (key.startsWith(AI_PREFIX) || key.startsWith(TRUST_PREFIX)) {
                throw new BusinessException(ErrorCode.CONFIG_KEY_UNKNOWN,
                        "Khóa '" + key + "' thuộc cấu hình AI — vui lòng dùng PUT /api/admin/ai/config");
            }

            SystemConfig config = systemConfigRepository.findByConfigKeyAndDeletedAtIsNull(key)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CONFIG_KEY_UNKNOWN,
                            "Khóa cấu hình không tồn tại: " + key));

            Object oldTyped = systemConfigMapper.coerce(config.getConfigValue(), config.getValueType());
            String newRaw = stringify(entry.getValue());

            try {
                systemConfigService.updateValue(key, newRaw, actorId);
            } catch (BusinessException ex) {
                if (ex.getErrorCode() == ErrorCode.VALIDATION_FAILED) {
                    throw new BusinessException(ErrorCode.CONFIG_VALUE_INVALID,
                            "Giá trị cấu hình không hợp lệ cho khóa " + key + ": " + ex.getMessage());
                }
                throw ex;
            }

            Object newTyped = systemConfigMapper.coerce(newRaw, config.getValueType());
            Long auditId = auditLogService.recordChange(AuditAction.SYSTEM_CONFIG_CHANGE, actorId,
                    "CONFIG", null, key,
                    String.valueOf(config.getConfigValue()), newRaw, request.getReason());
            auditIds.add(auditId);
            updated.add(UpdateConfigResponse.UpdatedItem.builder()
                    .key(key).oldValue(oldTyped).newValue(newTyped).build());
        }

        return UpdateConfigResponse.builder()
                .updated(updated)
                .cacheInvalidated(true)
                .auditLogIds(auditIds)
                .note("Thay đổi cấu hình không hồi tố: chỉ áp dụng cho các thao tác kể từ thời điểm này")
                .updatedAt(Instant.now())
                .build();
    }

    /** Chuẩn hóa Object (từ JSON body) về chuỗi thô để lưu (khớp cột config_value TEXT). */
    private String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Boolean b) {
            return b.toString();
        }
        return String.valueOf(value);
    }
}
