package com.webtro.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Cài đặt cho {@link ValidPassword}. Quy tắc {@code [§3.1]}: >= 8 ký tự, có ít nhất một chữ cái
 * và một chữ số. Null bị coi là không hợp lệ (mật khẩu luôn bắt buộc khi dùng annotation này).
 */
public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {

    private static final int MIN_LENGTH = 8;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.length() < MIN_LENGTH) {
            return false;
        }
        boolean hasLetter = value.chars().anyMatch(Character::isLetter);
        boolean hasDigit = value.chars().anyMatch(Character::isDigit);
        return hasLetter && hasDigit;
    }
}
