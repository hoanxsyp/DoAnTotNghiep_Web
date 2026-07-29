package com.webtro.validator;

import com.webtro.util.PhoneUtil;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Cài đặt cho {@link ValidPhone}. Null/blank được coi là hợp lệ ở đây — dùng {@code @NotBlank}
 * riêng nếu muốn bắt buộc.
 */
public class PhoneValidator implements ConstraintValidator<ValidPhone, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return PhoneUtil.isValid(value);
    }
}
