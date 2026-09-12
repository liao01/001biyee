package com.jiawa.lyw.identity.infrastructure;

import cn.hutool.core.util.IdUtil;
import com.jiawa.lyw.identity.domain.SessionTokens;
import com.jiawa.lyw.identity.domain.IdentityException;
import com.jiawa.lyw.identity.domain.MemberAccount;

import java.time.Clock;
import java.time.Instant;

public final class RefreshSessionService {
    private final IdentityMapper mapper;
    private final JwtAccessTokenService accessTokens;
    private final IdentityProperties properties;
    private final Clock clock;

    public RefreshSessionService(IdentityMapper mapper, JwtAccessTokenService accessTokens,
                                 IdentityProperties properties, Clock clock) {
        this.mapper = mapper;
        this.accessTokens = accessTokens;
        this.properties = properties;
        this.clock = clock;
    }

    public SessionTokens issue(long memberId) {
        Instant now = clock.instant();
        Instant accessExpiresAt = now.plus(properties.accessTokenTtl());
        Instant refreshExpiresAt = now.plus(properties.refreshTokenTtl());
        String rawRefresh = OpaqueTokens.create();
        mapper.insertRefreshSession(IdUtil.getSnowflakeNextId(), memberId, OpaqueTokens.digest(rawRefresh), refreshExpiresAt, now);
        return new SessionTokens(accessTokens.issue(memberId, now, accessExpiresAt), accessExpiresAt, rawRefresh, refreshExpiresAt);
    }

    public SessionTokens rotate(String rawToken) {
        if (!OpaqueTokens.isValid(rawToken)) {
            throw invalidSession();
        }
        String digest = OpaqueTokens.digest(rawToken);
        Long memberId = mapper.findRefreshMemberId(digest);
        if (memberId == null) {
            throw invalidSession();
        }
        // 全部身份写入先锁账户，再锁令牌；与密码重置保持一致，避免交叉锁顺序。
        MemberAccount account = mapper.findAccountByIdForUpdate(memberId);
        if (account == null || account.accountStatus() != MemberAccount.AccountStatus.ACTIVE
                || account.emailVerifiedAt() == null || mapper.consumeRefreshSession(digest, clock.instant()) != 1) {
            throw invalidSession();
        }
        return issue(memberId);
    }

    public void revoke(String rawToken) {
        if (OpaqueTokens.isValid(rawToken)) {
            mapper.revokeRefreshSession(OpaqueTokens.digest(rawToken), clock.instant());
        }
    }

    private IdentityException invalidSession() {
        return new IdentityException(IdentityException.Reason.UNAUTHENTICATED, "登录会话已失效，请重新登录");
    }
}
