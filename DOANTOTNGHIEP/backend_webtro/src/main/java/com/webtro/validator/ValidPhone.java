package com.webtro.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Kiểm tra số điện thoại Việt Nam hợp lệ {@code [§3.1]}. Cho phép null (kết hợp với
 * {@code @NotBlank} khi cần bắt buộc).
 */
@Documented
@Constraint(validatedBy = PhoneValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPhone {

    String message() default "Số điện thoại không hợp lệ (phải là số di động Việt Nam)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
