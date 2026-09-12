package com.jiawa.lyw.identity.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyTests {
    @Test
    void newPasswordsRequireTenCharactersWithLettersAndDigitsWithinTheBcryptByteLimit() {
        assertDoesNotThrow(() -> PasswordPolicy.validateNewPassword("Travel12345"));
        assertDoesNotThrow(() -> PasswordPolicy.validateNewPassword("a".repeat(71) + "1"));
        assertDoesNotThrow(() -> PasswordPolicy.validateNewPassword("旅行计划安全密码123"));

        for (String invalid : new String[] {null, "", "Short123", "abcdefghij", "1234567890", "密".repeat(24) + "1"}) {
            assertThrows(IdentityException.class, () -> PasswordPolicy.validateNewPassword(invalid));
        }
    }
}
