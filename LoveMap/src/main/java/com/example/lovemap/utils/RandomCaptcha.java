package com.example.lovemap.utils;

import org.springframework.stereotype.Component;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * 随机验证码生成工具
 */
@Component
public class RandomCaptcha {

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final String DIGITS = "0123456789";

    private final SecureRandom random;

    public RandomCaptcha() {
        SecureRandom instance;
        try {
            instance = SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            instance = new SecureRandom();
        }
        this.random = instance;
    }

    /**
     * 生成随机字母数字验证码
     */
    public String generateRandomCode(int length) {
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return code.toString();
    }

    /**
     * 生成纯数字验证码
     */
    public String generateDigitCode(int length) {
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
        }
        return code.toString();
    }
}
