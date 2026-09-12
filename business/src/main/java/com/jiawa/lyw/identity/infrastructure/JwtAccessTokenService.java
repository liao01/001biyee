package com.jiawa.lyw.identity.infrastructure;

import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.jiawa.lyw.identity.domain.IdentityException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
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

    public long verifyMemberId(String rawToken, Instant now, Duration maximumLifetime) {
        try {
            if (rawToken == null || rawToken.length() > 2048 || !rawToken.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")) {
                throw invalidToken();
            }
            var jwt = JWTUtil.parseToken(rawToken);
            if (!"HS256".equals(jwt.getAlgorithm()) || !jwt.verify(JWTSignerUtil.hs256(signingKey))
                    || !jwt.getPayloads().keySet().equals(Set.of("sub", "iat", "nbf", "exp", "jti"))) {
                throw invalidToken();
            }
            long issuedAt = numericDate(jwt.getPayload("iat"));
            long notBefore = numericDate(jwt.getPayload("nbf"));
            long expiresAt = numericDate(jwt.getPayload("exp"));
            long seconds = now.getEpochSecond();
            if (issuedAt <= 0 || notBefore != issuedAt || issuedAt > seconds || expiresAt <= seconds
                    || expiresAt <= issuedAt || expiresAt > Math.addExact(issuedAt, maximumLifetime.toSeconds())) {
                throw invalidToken();
            }
            Object subject = jwt.getPayload("sub");
            Object tokenId = jwt.getPayload("jti");
            if (!(subject instanceof String id) || !id.matches("[1-9][0-9]{0,18}")
                    || !(tokenId instanceof String jti) || !UUID.fromString(jti).toString().equals(jti)) {
                throw invalidToken();
            }
            return Long.parseLong(id);
        } catch (RuntimeException ignored) {
            throw invalidToken();
        }
    }

    private long numericDate(Object value) {
        if (!(value instanceof Integer) && !(value instanceof Long)) {
            throw invalidToken();
        }
        return ((Number) value).longValue();
    }

    private IdentityException invalidToken() {
        return new IdentityException(IdentityException.Reason.UNAUTHENTICATED, "登录凭据已失效，请重新登录");
    }
}
