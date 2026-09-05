package com.jiawa.lyw.identity.domain;

import java.time.Instant;

public record SessionTokens(
        String accessToken,
        Instant accessExpiresAt,
        String refreshToken,
        Instant refreshExpiresAt) {
    @Override
    public String toString() {
        return "SessionTokens[redacted]";
    }
}
