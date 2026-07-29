package com.webtro.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Từ chối chuỗi có chứa thẻ HTML/script ngay tại tầng validation {@code [§11.1]} — lớp phòng thủ
 * đầu tiên chống XSS, trước cả khi {@code HtmlSanitizer} làm sạch để lưu.
 */
@Documented
@Constraint(validatedBy = NoHtmlValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoHtml {

    String message() default "Nội dung không được chứa mã HTML hoặc script";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
