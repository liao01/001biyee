package com.jiawa.lyw;

import com.jiawa.lyw.Util.JwtUtil;
import com.jiawa.lyw.Util.MailUtils;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeSecretConfigurationTests {
    @Test
    void jwtSecretMustBeAtLeastThirtyTwoCharacters() {
        assertThrows(IllegalArgumentException.class, () -> new JwtUtil("too-short"));
    }

    @Test
    void configuredJwtSecretSignsAndValidatesTokens() {
        new JwtUtil("local-test-signing-secret-at-least-32-bytes");

        String token = JwtUtil.createLoginToken(Map.of("memberId", 42L));

        assertTrue(JwtUtil.validate(token));
    }

    @Test
    void mailCredentialsCannotBeBlank() {
        assertThrows(IllegalArgumentException.class, () -> new MailUtils("", "auth-code"));
        assertThrows(IllegalArgumentException.class, () -> new MailUtils("sender", ""));
    }
}
