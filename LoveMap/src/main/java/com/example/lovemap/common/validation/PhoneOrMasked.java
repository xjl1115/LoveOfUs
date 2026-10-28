package com.example.lovemap.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 手机号校验：允许为空、合法 11 位手机号、或后端脱敏占位串（前3位+****+后4位）。
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PhoneOrMaskedValidator.class)
public @interface PhoneOrMasked {

    String message() default "请输入正确的手机号";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
