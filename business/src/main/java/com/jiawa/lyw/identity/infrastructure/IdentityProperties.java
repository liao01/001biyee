package com.jiawa.lyw.identity.infrastructure;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;

public record IdentityProperties(
        URI publicUrl,
        Duration verificationTtl,
        Duration passwordResetTtl,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        boolean secureCookie) {
    public IdentityProperties {
        if (publicUrl == null || !Set.of("http", "https").contains(publicUrl.getScheme())
                || publicUrl.getHost() == null || publicUrl.getUserInfo() != null
                || publicUrl.getQuery() != null || publicUrl.getFragment() != null) {
            throw new IllegalArgumentException("app.public-url must be an absolute http(s) base URL without credentials, query or fragment");
        }
        for (Duration ttl : List.of(verificationTtl, passwordResetTtl, accessTokenTtl, refreshTokenTtl)) {
            if (ttl.isZero() || ttl.isNegative()) {
                throw new IllegalArgumentException("Identity token lifetimes must be positive");
            }
        }
    }
}
