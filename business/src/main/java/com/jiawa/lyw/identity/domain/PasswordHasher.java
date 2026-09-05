package com.jiawa.lyw.identity.domain;

public interface PasswordHasher {
    int MAXIMUM_UTF8_BYTES = 72;

    String hash(String rawPassword);

    PasswordCheck verify(String rawPassword, String storedHash, String algorithm);

    record PasswordCheck(boolean matches, boolean needsUpgrade) {
    }
}
