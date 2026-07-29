package com.webtro.modules.admin.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webtro.common.enums.ConfigValueType;
import com.webtro.modules.admin.dto.response.ConfigItemResponse;
import com.webtro.modules.admin.entity.SystemConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Mapper thủ công {@link SystemConfig} → {@link ConfigItemResponse} (Builder, không MapStruct).
 * Ép {@code config_value}/{@code default_value} về đúng kiểu {@code value_type} để frontend nhận
 * đúng number/boolean/string thay vì luôn là chuỗi.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemConfigMapper {

    private final ObjectMapper objectMapper;

    /** Chuyển một bản ghi cấu hình sang DTO hiển thị. */
    public ConfigItemResponse toResponse(SystemConfig e) {
        return ConfigItemResponse.builder()
                .key(e.getConfigKey())
                .value(coerce(e.getConfigValue(), e.getValueType()))
                .type(e.getValueType() != null ? e.getValueType().name() : null)
                .defaultValue(coerce(e.getDefaultValue(), e.getValueType()))
                .min(e.getMinValue())
                .max(e.getMaxValue())
                .label(e.getLabel())
                .description(e.getDescription())
                .editable(e.getIsEditable())
                .build();
    }

    /** Ép một chuỗi thô về Object đúng kiểu để serialize JSON đúng dạng. */
    public Object coerce(String raw, ConfigValueType type) {
        if (raw == null) {
            return null;
        }
        if (type == null) {
            return raw;
        }
        try {
            return switch (type) {
                case INT -> Long.parseLong(raw.trim());
                case DECIMAL -> new BigDecimal(raw.trim());
                case BOOLEAN -> "true".equalsIgnoreCase(raw.trim()) || "1".equals(raw.trim());
                case JSON -> objectMapper.readTree(raw);
                case STRING -> raw;
            };
        } catch (Exception ex) {
            log.debug("Không ép được cấu hình '{}' về kiểu {}: {}", raw, type, ex.getMessage());
            return raw;
        }
    }
}
