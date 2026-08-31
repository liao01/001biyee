package com.jiawa.lyw.identity.infrastructure;

import com.jiawa.lyw.identity.application.DefaultIdentityApplicationService;
import com.jiawa.lyw.identity.application.IdentityApplicationService;
import com.jiawa.lyw.identity.domain.PasswordHasher;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.net.URI;
import java.time.Clock;

@Configuration
@MapperScan(basePackageClasses = IdentityMapper.class, annotationClass = Mapper.class)
public class IdentityConfiguration {
    @Bean
    Clock identityClock() { return Clock.systemUTC(); }

    @Bean
    PasswordHasher passwordHasher() { return new BCryptPasswordHasher(); }

    @Bean
    IdentityProperties identityProperties(Environment environment) {
        return new IdentityProperties(
                URI.create(environment.getProperty("app.public-url", "http://localhost:5173")),
                DurationStyle.detectAndParse(environment.getProperty("identity.verification-ttl", "24h")),
                DurationStyle.detectAndParse(environment.getProperty("identity.password-reset-ttl", "30m")),
                DurationStyle.detectAndParse(environment.getProperty("identity.access-token-ttl", "15m")),
                DurationStyle.detectAndParse(environment.getProperty("identity.refresh-token-ttl", "30d")),
                environment.acceptsProfiles(Profiles.of("prod")) || environment.getProperty("identity.secure-cookie", Boolean.class, true));
    }

    @Bean
    JwtAccessTokenService jwtAccessTokenService(@Value("${jwt.secret}") String secret) {
        return new JwtAccessTokenService(secret);
    }

    @Bean
    RefreshSessionService refreshSessionService(IdentityMapper mapper, JwtAccessTokenService tokens,
                                                 IdentityProperties properties, Clock clock) {
        return new RefreshSessionService(mapper, tokens, properties, clock);
    }

    @Bean
    OneTimeTokenService oneTimeTokenService(IdentityMapper mapper, IdentityProperties properties, Clock clock) {
        return new OneTimeTokenService(mapper, properties, clock);
    }

    @Bean
    IdentityApplicationService identityApplicationService(IdentityMapper mapper, PasswordHasher passwords,
                                                          RefreshSessionService sessions, Clock clock,
                                                          OneTimeTokenService oneTimeTokens, IdentityMailGateway mail) {
        return new DefaultIdentityApplicationService(mapper, passwords, sessions, clock, oneTimeTokens, mail);
    }
}
