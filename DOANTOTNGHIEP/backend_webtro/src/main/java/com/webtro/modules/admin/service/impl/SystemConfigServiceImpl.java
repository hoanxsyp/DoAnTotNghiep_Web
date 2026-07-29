package com.webtro.modules.admin.service.impl;

import com.webtro.common.enums.ConfigValueType;
import com.webtro.constant.CacheName;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessException;
import com.webtro.modules.admin.entity.SystemConfig;
import com.webtro.modules.admin.repository.SystemConfigRepository;
import com.webtro.modules.admin.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * Cài đặt {@link SystemConfigService}. Cache từng key trong Redis ({@link CacheName#SYSTEM_CONFIG});
 * mọi lần đọc đi qua {@link #getRawValue} (được cache), các hàm {@code getX} chỉ ép kiểu.
 *
 * <p>Ép kiểu an toàn: nếu giá trị lưu không đúng {@code value_type}, ném lỗi cấu hình rõ ràng thay
 * vì trả về mặc định âm thầm — sai cấu hình phải nổ ra để phát hiện sớm.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements SystemConfigService {

    private final SystemConfigRepository repository;

    @Override
    @Cacheable(cacheNames = CacheName.SYSTEM_CONFIG, key = "#key")
    @Transactional(readOnly = true)
    public String getString(String key) {
        return getEntity(key).getConfigValue();
    }

    @Override
    public int getInt(String key) {
        try {
            return Integer.parseInt(getString(key).trim());
        } catch (NumberFormatException e) {
            throw configTypeError(key, "INT");
        }
    }

    @Override
    public long getLong(String key) {
        try {
            return Long.parseLong(getString(key).trim());
        } catch (NumberFormatException e) {
            throw configTypeError(key, "LONG");
        }
    }

    @Override
    public BigDecimal getDecimal(String key) {
        try {
            return new BigDecimal(getString(key).trim());
        } catch (NumberFormatException e) {
            throw configTypeError(key, "DECIMAL");
        }
    }

    @Override
    public double getDouble(String key) {
        return getDecimal(key).doubleValue();
    }

    @Override
    public boolean getBoolean(String key) {
        String v = getString(key).trim();
        return "true".equalsIgnoreCase(v) || "1".equals(v);
    }

    @Override
    public List<Integer> getIntList(String key) {
        String raw = getString(key).trim();
        if (raw.isEmpty()) {
            return List.of();
        }
        try {
            return Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Integer::parseInt)
                    .toList();
        } catch (NumberFormatException e) {
            throw configTypeError(key, "INT_LIST");
        }
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheName.SYSTEM_CONFIG, key = "#key")
    public String updateValue(String key, String newValue, Long actorId) {
        SystemConfig config = getEntity(key);
        if (Boolean.FALSE.equals(config.getIsEditable())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATED,
                    "Cấu hình '" + key + "' không cho phép chỉnh sửa");
        }
        validateValue(config, newValue);
        config.setConfigValue(newValue);
        repository.save(config);
        log.info("Admin {} cập nhật cấu hình {} = {}", actorId, key, newValue);
        return newValue;
    }

    @Override
    @CacheEvict(cacheNames = CacheName.SYSTEM_CONFIG, allEntries = true)
    public void evictCache(String key) {
        // allEntries=true để đơn giản; số key ít nên chi phí không đáng kể.
    }

    // ==================================================================

    private SystemConfig getEntity(String key) {
        return repository.findByConfigKeyAndDeletedAtIsNull(key)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "Thiếu cấu hình hệ thống bắt buộc: " + key));
    }

    private void validateValue(SystemConfig config, String newValue) {
        ConfigValueType type = config.getValueType();
        if (type == ConfigValueType.INT || type == ConfigValueType.DECIMAL) {
            BigDecimal num;
            try {
                num = new BigDecimal(newValue.trim());
            } catch (NumberFormatException e) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "Giá trị cấu hình phải là số: " + config.getConfigKey());
            }
            if (config.getMinValue() != null && num.compareTo(config.getMinValue()) < 0) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "Giá trị nhỏ hơn mức tối thiểu " + config.getMinValue());
            }
            if (config.getMaxValue() != null && num.compareTo(config.getMaxValue()) > 0) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "Giá trị lớn hơn mức tối đa " + config.getMaxValue());
            }
        } else if (type == ConfigValueType.BOOLEAN) {
            String v = newValue.trim().toLowerCase();
            if (!v.equals("true") && !v.equals("false")) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "Giá trị phải là true/false: " + config.getConfigKey());
            }
        }
    }

    private BusinessException configTypeError(String key, String expectedType) {
        return new BusinessException(ErrorCode.INTERNAL_ERROR,
                "Cấu hình '" + key + "' không ép được về kiểu " + expectedType);
    }
}
