package com.webtro.validator;

import com.webtro.util.HtmlSanitizer;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Cài đặt cho {@link NoHtml}. Null/blank hợp lệ (kết hợp {@code @NotBlank} nếu cần bắt buộc).
 */
public class NoHtmlValidator implements ConstraintValidator<NoHtml, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return !HtmlSanitizer.containsHtml(value);
    }
}
