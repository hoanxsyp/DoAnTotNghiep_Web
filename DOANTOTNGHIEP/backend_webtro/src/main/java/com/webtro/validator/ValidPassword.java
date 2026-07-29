package com.webtro.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Kiểm tra độ mạnh mật khẩu {@code [§3.1]}: tối thiểu 8 ký tự, có chữ và số.
 */
@Documented
@Constraint(validatedBy = PasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

    String message() default "Mật khẩu phải có tối thiểu 8 ký tự, gồm cả chữ và số";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
