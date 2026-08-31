package com.jiawa.lyw.identity.infrastructure;

import com.jiawa.lyw.identity.application.CurrentMemberProvider;
import com.jiawa.lyw.identity.domain.IdentityException;
import com.jiawa.lyw.identity.domain.MemberProfile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.util.Collections;

public final class HttpCurrentMemberProvider implements CurrentMemberProvider {
    private static final String PROFILE_ATTRIBUTE = HttpCurrentMemberProvider.class.getName() + ".profile";
    private final IdentityMapper mapper;
    private final JwtAccessTokenService tokens;
    private final IdentityProperties properties;
    private final Clock clock;

    public HttpCurrentMemberProvider(IdentityMapper mapper, JwtAccessTokenService tokens, IdentityProperties properties, Clock clock) {
        this.mapper = mapper;
        this.tokens = tokens;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public long memberId() { return profile().id(); }

    public MemberProfile profile() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            throw unauthenticated();
        }
        var request = attributes.getRequest();
        if (request.getAttribute(PROFILE_ATTRIBUTE) instanceof MemberProfile profile) {
            return profile;
        }
        var headers = Collections.list(request.getHeaders("Authorization"));
        if (headers.size() != 1) { throw unauthenticated(); }
        String authorization = headers.get(0);
        if (!authorization.regionMatches(true, 0, "Bearer ", 0, 7)) { throw unauthenticated(); }
        long id = tokens.verifyMemberId(authorization.substring(7), clock.instant(), properties.accessTokenTtl());
        MemberProfile profile = mapper.findActiveProfile(id);
        if (profile == null) { throw unauthenticated(); }
        request.setAttribute(PROFILE_ATTRIBUTE, profile);
        return profile;
    }

    private IdentityException unauthenticated() {
        return new IdentityException(IdentityException.Reason.UNAUTHENTICATED, "请先登录");
    }
}
