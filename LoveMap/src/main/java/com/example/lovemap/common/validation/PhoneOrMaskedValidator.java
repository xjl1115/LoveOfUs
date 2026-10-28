package com.example.lovemap.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * 手机号校验器实现：
 * - null / 空字符串：通过
 * - 11 位合法手机号（^1[3-9]\d{9}$）：通过
 * - 脱敏占位串（^\d{3}\*{4}\d{4}$）：通过
 * - 其他：校验失败
 */
public class PhoneOrMaskedValidator implements ConstraintValidator<PhoneOrMasked, String> {

    private static final Pattern RAW_PHONE = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern MASKED_PHONE = Pattern.compile("^\\d{3}\\*{4}\\d{4}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        return RAW_PHONE.matcher(value).matches() || MASKED_PHONE.matcher(value).matches();
    }
}
