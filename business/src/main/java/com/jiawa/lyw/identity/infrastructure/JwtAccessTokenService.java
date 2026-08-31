package com.jiawa.lyw.identity.infrastructure;

import cn.hutool.jwt.JWTUtil;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class JwtAccessTokenService {
    private final byte[] signingKey;

    public JwtAccessTokenService(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("jwt.secret must contain at least 32 UTF-8 bytes");
        }
        signingKey = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String issue(long memberId, Instant now, Instant expiresAt) {
        return JWTUtil.createToken(Map.of(
                "sub", Long.toString(memberId),
                "iat", now.getEpochSecond(),
                "nbf", now.getEpochSecond(),
                "exp", expiresAt.getEpochSecond(),
                "jti", UUID.randomUUID().toString()), signingKey);
    }
}
