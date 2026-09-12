package com.jiawa.lyw.identity.domain;

import java.nio.charset.StandardCharsets;

/** 新注册和重置密码共用的规则；旧密码验证不应用新的最小长度规则。 */
public final class PasswordPolicy {
    private PasswordPolicy() {
    }

    public static void validateNewPassword(String rawPassword) {
        if (rawPassword == null
                || rawPassword.codePointCount(0, rawPassword.length()) < 10
                || rawPassword.getBytes(StandardCharsets.UTF_8).length > PasswordHasher.MAXIMUM_UTF8_BYTES
                || rawPassword.codePoints().noneMatch(Character::isLetter)
                || rawPassword.codePoints().noneMatch(Character::isDigit)) {
            throw new IdentityException("密码至少 10 个字符，包含字母和数字，UTF-8 编码不超过 72 字节");
        }
    }
}
