package com.jiawa.lyw.identity.domain;

public interface PasswordHasher {
    String hash(String rawPassword);

    PasswordCheck verify(String rawPassword, String storedHash, String algorithm);

    record PasswordCheck(boolean matches, boolean needsUpgrade) {
    }
}
