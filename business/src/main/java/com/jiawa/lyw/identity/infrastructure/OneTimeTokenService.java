package com.jiawa.lyw.identity.infrastructure;

import cn.hutool.core.util.IdUtil;
import com.jiawa.lyw.identity.domain.IdentityException;
import com.jiawa.lyw.identity.domain.MemberAccount;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;

public final class OneTimeTokenService {
    public enum Purpose { VERIFY_EMAIL, RESET_PASSWORD }

    private final IdentityMapper mapper;
    private final IdentityProperties properties;
    private final Clock clock;

    public OneTimeTokenService(IdentityMapper mapper, IdentityProperties properties, Clock clock) {
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
    }

    public URI issue(long memberId, String email, Purpose purpose) {
        Instant now = clock.instant();
        mapper.invalidateOneTimeTokens(memberId, purpose, now);
        String rawToken = OpaqueTokens.create();
        Instant expiresAt = now.plus(purpose == Purpose.VERIFY_EMAIL ? properties.verificationTtl() : properties.passwordResetTtl());
        mapper.insertOneTimeToken(IdUtil.getSnowflakeNextId(), memberId, email, purpose, OpaqueTokens.digest(rawToken), expiresAt, now);
        String base = properties.publicUrl().toASCIIString().replaceAll("/+$", "");
        return URI.create(base + (purpose == Purpose.VERIFY_EMAIL ? "/verify-email" : "/reset-password") + "?token=" + rawToken);
    }

    public MemberAccount consume(String rawToken, Purpose purpose) {
        if (!OpaqueTokens.isValid(rawToken)) {
            throw invalidToken();
        }
        String digest = OpaqueTokens.digest(rawToken);
        Long memberId = mapper.findOneTimeTokenMemberId(digest, purpose);
        if (memberId == null) {
            throw invalidToken();
        }
        MemberAccount account = mapper.findAccountByIdForUpdate(memberId);
        if (account == null || mapper.consumeOneTimeToken(digest, purpose, account.email(), clock.instant()) != 1) {
            throw invalidToken();
        }
        return account;
    }

    private IdentityException invalidToken() {
        return new IdentityException(IdentityException.Reason.INVALID_TOKEN, "链接已失效，请重新申请");
    }
}
