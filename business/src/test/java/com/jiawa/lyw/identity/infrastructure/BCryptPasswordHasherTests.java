package com.jiawa.lyw.identity.infrastructure;

import com.jiawa.lyw.identity.domain.PasswordHasher;
import com.jiawa.lyw.identity.domain.IdentityException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BCryptPasswordHasherTests {
    private final PasswordHasher hasher = new BCryptPasswordHasher();

    @Test
    void matchingLegacyDoubleMd5CredentialRequiresUpgrade() {
        // 独立计算的公开测试向量，输入为下方测试口令，不是用户凭据。
        String legacyHash = "b406cd63d1530b73" + "464838f07947ccca";

        var result = hasher.verify("Test-password-123", legacyHash, "LEGACY_DOUBLE_MD5");

        assertTrue(result.matches());
        assertTrue(result.needsUpgrade());
        assertFalse(hasher.verify("wrong-password", legacyHash, "LEGACY_DOUBLE_MD5").matches());
    }

    @Test
    void newCredentialsUseSaltedCostTwelveBcryptAndMatchOnlyTheOriginalPassword() {
        String first = hasher.hash("Test-password-123");
        String second = hasher.hash("Test-password-123");

        assertTrue(first.startsWith("$2a$12$"));
        assertNotEquals(first, second);
        assertTrue(hasher.verify("Test-password-123", first, "BCRYPT").matches());
        assertFalse(hasher.verify("Test-password-123", first, "BCRYPT").needsUpgrade());
        assertFalse(hasher.verify("test-password-123", first, "BCRYPT").matches());
    }

    @Test
    void unsupportedOrOversizedCredentialsFailClosedWithoutTruncation() {
        String oversized = "密".repeat(25);
        var error = assertThrows(IdentityException.class, () -> hasher.hash(oversized));

        assertFalse(error.getMessage().contains(oversized));
        assertThrows(IdentityException.class, () -> hasher.hash(""));
        assertThrows(IdentityException.class, () -> hasher.hash(null));
        assertFalse(hasher.verify(oversized, hasher.hash("a".repeat(72)), "BCRYPT").matches());
        assertFalse(hasher.verify("Test-password-123", "malformed", "BCRYPT").matches());
        assertFalse(hasher.verify("Test-password-123", "anything", "UNKNOWN").matches());
    }
}
