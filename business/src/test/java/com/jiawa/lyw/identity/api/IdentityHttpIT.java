package com.jiawa.lyw.identity.api;

import com.jiawa.lyw.identity.infrastructure.IdentityConfiguration;
import com.jiawa.lyw.identity.infrastructure.IdentityMailGateway;
import com.jiawa.lyw.config.SpringMvcConfig;
import com.jiawa.lyw.interceptor.WebLoginInterceptor;
import com.jiawa.lyw.interceptor.AdminLoginInterceptor;
import com.jiawa.lyw.interceptor.LogInterceptor;
import com.jiawa.lyw.aspect.LogAspect;
import com.jiawa.lyw.controller.web.MemberController;
import com.jiawa.lyw.controller.ControllerExceptionHandler;
import com.jiawa.lyw.service.MemberLoginLogService;
import com.jiawa.lyw.mapper.MemberLoginLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiawa.lyw.support.MySqlIntegrationDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.sql.DataSource;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = IdentityHttpIT.Config.class)
@ActiveProfiles("prod")
@TestPropertySource(properties = {"jwt.secret=integration-test-signing-key-at-least-32-bytes",
        "app.public-url=https://travel.example.test/travel", "identity.secure-cookie=false"})
class IdentityHttpIT {
    private static final MySqlIntegrationDatabase DATABASE = new MySqlIntegrationDatabase();

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private TestMailGateway mail;
    @Autowired
    private TestClock clock;
    private MockMvc mvc;
    private JdbcTemplate jdbc;

    @BeforeEach
    void prepareIsolatedAccount() {
        clock.now = Instant.parse("2026-08-31T00:00:00Z");
        mail.verificationLink = null;
        mail.resetLink = null;
        mail.failDelivery = false;
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM identity_one_time_token");
        jdbc.update("DELETE FROM identity_refresh_session");
        jdbc.update("DELETE FROM member_login_log");
        jdbc.update("DELETE FROM member");
        String legacyHash = "b406cd63d1530b73" + "464838f07947ccca";
        jdbc.update("INSERT INTO member (id, email, email_verified_at, password_hash, password_algorithm, account_status, password, name) "
                        + "VALUES (42, ?, CURRENT_TIMESTAMP(3), ?, 'LEGACY_DOUBLE_MD5', 'ACTIVE', ?, 'TEST legacy account')",
                "legacy@example.com", legacyHash, legacyHash);
    }

    @AfterAll
    static void removeIsolatedDatabase() {
        DATABASE.close();
    }

    @Test
    void legacyEmailLoginImmediatelyUpgradesTheCredentialAndKeepsRefreshTokenOutOfJson() throws Exception {
        var response = mvc.perform(post("/web/identity/login").contentType("application/json")
                        .content("{\"email\":\"Legacy@Example.com\",\"password\":\"Test-password-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.accessToken").isString())
                .andExpect(jsonPath("$.content.refreshToken").doesNotExist())
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(cookie().secure("refresh_token", true))
                .andReturn().getResponse();

        var json = new ObjectMapper().readTree(response.getContentAsString());
        String accessToken = json.path("content").path("accessToken").asText();
        var claims = new ObjectMapper().readTree(Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]));
        Set<String> fields = new HashSet<>();
        claims.fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("sub", "iat", "nbf", "exp", "jti"), fields);
        assertEquals("42", claims.path("sub").asText());
        assertEquals(900, claims.path("exp").asLong() - claims.path("iat").asLong());
        assertTrue(cn.hutool.jwt.JWTUtil.verify(accessToken, "integration-test-signing-key-at-least-32-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        String rawRefresh = response.getCookie("refresh_token").getValue();
        assertTrue(rawRefresh.matches("[A-Za-z0-9_-]{43}"));
        assertEquals(30 * 24 * 60 * 60, response.getCookie("refresh_token").getMaxAge());
        assertTrue(response.getHeader("Set-Cookie").contains("SameSite=Lax"));
        assertNotEquals(rawRefresh, jdbc.queryForObject("SELECT token_hash FROM identity_refresh_session WHERE member_id = 42", String.class));

        // 持久化安全不变量：旧凭据副本清除，正式凭据收敛为加盐 BCrypt。
        assertNull(jdbc.queryForObject("SELECT password FROM member WHERE id = 42", String.class));
        assertTrue(jdbc.queryForObject("SELECT password_hash FROM member WHERE id = 42", String.class).startsWith("$2a$12$"));
    }

    @Test
    void refreshRotatesOnceAndLogoutRevokesTheNewSession() throws Exception {
        var first = loginLegacy().getCookie("refresh_token");
        var rotated = mvc.perform(post("/web/identity/refresh").cookie(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.accessToken").isString())
                .andExpect(jsonPath("$.content.refreshToken").doesNotExist())
                .andReturn().getResponse().getCookie("refresh_token");
        assertNotEquals(first.getValue(), rotated.getValue());
        mvc.perform(post("/web/identity/refresh").cookie(first)).andExpect(status().isUnauthorized());
        mvc.perform(post("/web/identity/logout").cookie(rotated))
                .andExpect(status().isOk()).andExpect(cookie().maxAge("refresh_token", 0));
        mvc.perform(post("/web/identity/refresh").cookie(rotated)).andExpect(status().isUnauthorized());
        mvc.perform(post("/web/identity/logout").cookie(rotated)).andExpect(status().isOk());
    }

    @Test
    void registeredEmailCannotLoginUntilItsSingleUseLinkIsVerified() throws Exception {
        String credentials = "{\"email\":\"Test-alice@Example.com\",\"password\":\"Test-password-123\"}";
        mvc.perform(post("/web/identity/register").contentType("application/json").content(credentials))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.token").doesNotExist());
        mvc.perform(post("/web/identity/login").contentType("application/json").content(credentials))
                .andExpect(status().isForbidden());
        assertEquals("test-alice@example.com", mail.recipient);
        assertEquals("/travel/verify-email", mail.verificationLink.getPath());
        String rawToken = mail.verificationLink.getQuery().substring("token=".length());
        assertTrue(rawToken.matches("[A-Za-z0-9_-]{43}"));
        // 持久化安全契约：一次性令牌与新账户不能遗留原文或旧凭据字段。
        assertNotEquals(rawToken, jdbc.queryForObject("SELECT token_hash FROM identity_one_time_token", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM member WHERE email = 'test-alice@example.com' "
                + "AND (mobile IS NOT NULL OR password IS NOT NULL)", Integer.class));
        String body = "{\"token\":\"" + rawToken + "\"}";
        mvc.perform(post("/web/identity/reset-password").contentType("application/json")
                .content("{\"token\":\"" + rawToken + "\",\"newPassword\":\"Replacement-password-456\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/web/identity/verify-email").contentType("application/json").content(body)).andExpect(status().isOk());
        mvc.perform(post("/web/identity/verify-email").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        mvc.perform(post("/web/identity/login").contentType("application/json").content(credentials)).andExpect(status().isOk());
    }

    @Test
    void passwordResetIsPrivateSingleUseAndRevokesEveryRefreshSession() throws Exception {
        var sessionA = loginLegacy().getCookie("refresh_token");
        var sessionB = loginLegacy().getCookie("refresh_token");
        String missing = mvc.perform(post("/web/identity/request-password-reset").contentType("application/json")
                        .content("{\"email\":\"missing@example.com\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertNull(mail.resetLink);
        String existing = mvc.perform(post("/web/identity/request-password-reset").contentType("application/json")
                        .content("{\"email\":\"legacy@example.com\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(missing, existing);
        assertEquals("/travel/reset-password", mail.resetLink.getPath());
        String rawToken = mail.resetLink.getQuery().substring("token=".length());
        String reset = "{\"token\":\"" + rawToken + "\",\"newPassword\":\"Replacement-password-456\"}";
        mvc.perform(post("/web/identity/verify-email").contentType("application/json")
                .content("{\"token\":\"" + rawToken + "\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/web/identity/reset-password").contentType("application/json").content(reset)).andExpect(status().isOk());
        mvc.perform(post("/web/identity/reset-password").contentType("application/json").content(reset)).andExpect(status().isBadRequest());
        mvc.perform(post("/web/identity/refresh").cookie(sessionA)).andExpect(status().isUnauthorized());
        mvc.perform(post("/web/identity/refresh").cookie(sessionB)).andExpect(status().isUnauthorized());
        mvc.perform(post("/web/identity/login").contentType("application/json")
                .content("{\"email\":\"legacy@example.com\",\"password\":\"Test-password-123\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/web/identity/login").contentType("application/json")
                .content("{\"email\":\"legacy@example.com\",\"password\":\"Replacement-password-456\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void failedResetEmailRollsBackAndDoesNotRevealAccountExistence() throws Exception {
        mvc.perform(post("/web/identity/request-password-reset").contentType("application/json")
                .content("{\"email\":\"legacy@example.com\"}")).andExpect(status().isOk());
        URI deliveredLink = mail.resetLink;
        mail.failDelivery = true;
        String missing = mvc.perform(post("/web/identity/request-password-reset").contentType("application/json")
                        .content("{\"email\":\"missing@example.com\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String failed = mvc.perform(post("/web/identity/request-password-reset").contentType("application/json")
                        .content("{\"email\":\"legacy@example.com\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(missing, failed);
        String rawToken = deliveredLink.getQuery().substring("token=".length());
        mvc.perform(post("/web/identity/reset-password").contentType("application/json")
                        .content("{\"token\":\"" + rawToken + "\",\"newPassword\":\"Replacement-password-456\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void concurrentRefreshOfTheSameCookieHasExactlyOneWinner() throws Exception {
        var refreshCookie = loginLegacy().getCookie("refresh_token");
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        java.util.concurrent.Callable<Integer> attempt = () -> {
            ready.countDown();
            assertTrue(start.await(5, TimeUnit.SECONDS));
            return mvc.perform(post("/web/identity/refresh").cookie(refreshCookie)).andReturn().getResponse().getStatus();
        };
        try {
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            var results = java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(code -> code == 200).count());
            assertEquals(1, results.stream().filter(code -> code == 401).count());
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"verification", "reset", "refresh"})
    void tokensAreRejectedExactlyAtTheirExpiry(String kind) throws Exception {
        if (kind.equals("refresh")) {
            var refreshCookie = loginLegacy().getCookie("refresh_token");
            clock.now = clock.now.plusSeconds(30L * 24 * 60 * 60);
            mvc.perform(post("/web/identity/refresh").cookie(refreshCookie)).andExpect(status().isUnauthorized());
        } else if (kind.equals("verification")) {
            mvc.perform(post("/web/identity/register").contentType("application/json")
                    .content("{\"email\":\"test-expiry@example.com\",\"password\":\"Test-password-123\"}")).andExpect(status().isOk());
            String rawToken = mail.verificationLink.getQuery().substring("token=".length());
            clock.now = clock.now.plusSeconds(24 * 60 * 60);
            mvc.perform(post("/web/identity/verify-email").contentType("application/json")
                    .content("{\"token\":\"" + rawToken + "\"}")).andExpect(status().isBadRequest());
        } else {
            mvc.perform(post("/web/identity/request-password-reset").contentType("application/json")
                    .content("{\"email\":\"legacy@example.com\"}")).andExpect(status().isOk());
            String rawToken = mail.resetLink.getQuery().substring("token=".length());
            clock.now = clock.now.plusSeconds(30 * 60);
            mvc.perform(post("/web/identity/reset-password").contentType("application/json")
                    .content("{\"token\":\"" + rawToken + "\",\"newPassword\":\"Replacement-password-456\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{}", "{\"email\":\"bad-address\",\"password\":\"Test-password-123\"}"})
    void malformedIdentityRequestsReturnClientErrorsWithoutEchoingInput(String body) throws Exception {
        mvc.perform(post("/web/identity/register").contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("请求参数不正确"));
    }

    @Test
    void reRegisteringPendingEmailBindsTheNewPasswordToOnlyTheLatestLink() throws Exception {
        String original = "{\"email\":\"test-retry@example.com\",\"password\":\"Test-password-123\"}";
        String replacement = "{\"email\":\"test-retry@example.com\",\"password\":\"Replacement-password-456\"}";
        mvc.perform(post("/web/identity/register").contentType("application/json").content(original)).andExpect(status().isOk());
        String first = mail.verificationLink.getQuery().substring("token=".length());
        mvc.perform(post("/web/identity/register").contentType("application/json").content(replacement)).andExpect(status().isOk());
        String second = mail.verificationLink.getQuery().substring("token=".length());
        mvc.perform(post("/web/identity/verify-email").contentType("application/json")
                .content("{\"token\":\"" + first + "\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/web/identity/verify-email").contentType("application/json")
                .content("{\"token\":\"" + second + "\"}")).andExpect(status().isOk());
        // 已验证账户再次收到匿名注册请求，不得被覆盖密码或退回待验证状态。
        mvc.perform(post("/web/identity/register").contentType("application/json").content(original)).andExpect(status().isOk());
        mvc.perform(post("/web/identity/login").contentType("application/json").content(original)).andExpect(status().isUnauthorized());
        mvc.perform(post("/web/identity/login").contentType("application/json").content(replacement)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void simultaneousRegistrationsDoNotFailForDistinctOrIdenticalEmails(boolean sameEmail) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var requests = java.util.List.of("test-first@example.com", sameEmail ? "test-first@example.com" : "test-second@example.com").stream()
                    .map(email -> executor.submit(() -> {
                        assertTrue(start.await(5, TimeUnit.SECONDS));
                        return mvc.perform(post("/web/identity/register").contentType("application/json")
                                        .content("{\"email\":\"" + email + "\",\"password\":\"Test-password-123\"}"))
                                .andReturn().getResponse().getStatus();
                    })).toList();
            start.countDown();
            for (var result : requests) {
                assertEquals(200, result.get(15, TimeUnit.SECONDS));
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void concurrentResetRequestsForDifferentAccountsDoNotDeadlock() throws Exception {
        jdbc.update("INSERT INTO member (id, email, email_verified_at, password_hash, password_algorithm, account_status, name) "
                + "SELECT 43, 'second@example.com', email_verified_at, password_hash, password_algorithm, account_status, 'TEST second account' "
                + "FROM member WHERE id = 42");
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var requests = java.util.List.of("legacy@example.com", "second@example.com").stream()
                    .map(email -> executor.submit(() -> {
                        assertTrue(start.await(5, TimeUnit.SECONDS));
                        return mvc.perform(post("/web/identity/request-password-reset").contentType("application/json")
                                        .content("{\"email\":\"" + email + "\"}"))
                                .andReturn().getResponse().getStatus();
                    })).toList();
            start.countDown();
            for (var result : requests) {
                assertEquals(200, result.get(15, TimeUnit.SECONDS));
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void realIdentityResponsesAndMailLinksAreAbsentFromApplicationLogs() throws Exception {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        var logs = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        logs.start();
        logger.addAppender(logs);
        try {
            var response = loginLegacy();
            String access = new ObjectMapper().readTree(response.getContentAsString()).path("content").path("accessToken").asText();
            String refresh = response.getCookie("refresh_token").getValue();
            mvc.perform(get("/web/identity/me").header("Authorization", "Bearer " + access))
                    .andExpect(status().isOk());
            mvc.perform(post("/web/identity/request-password-reset").contentType("application/json")
                    .content("{\"email\":\"legacy@example.com\"}")).andExpect(status().isOk());
            String linkToken = mail.resetLink.getQuery().substring("token=".length());
            assertTrue(logs.list.stream().anyMatch(event -> event.getFormattedMessage().contains("IdentityController.login")));
            for (var event : logs.list) {
                for (String secret : java.util.List.of("Test-password-123", access, refresh, linkToken)) {
                    assertFalse(event.getFormattedMessage().contains(secret));
                }
            }
        } finally {
            logger.detachAppender(logs);
            logs.stop();
        }
    }

    @Test
    void bearerAccessTokenRestoresOnlyTheAuthenticatedMemberWithoutLeakingCredentials() throws Exception {
        var login = loginLegacy();
        String access = new ObjectMapper().readTree(login.getContentAsString()).path("content").path("accessToken").asText();
        mvc.perform(get("/web/identity/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.id").value("42"))
                .andExpect(jsonPath("$.content.name").value("TEST legacy account"))
                .andExpect(jsonPath("$.content.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.content.token").doesNotExist());
        mvc.perform(get("/web/identity/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/web/identity/me").header("token", access)).andExpect(status().isUnauthorized());
        mvc.perform(get("/web/identity/me").header("Authorization", "Bearer " + access, "Bearer " + access))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"expired", "disabled", "tampered", "missing-claim", "extra-claim", "future", "wrong-algorithm"})
    void protectedIdentityReadRejectsInvalidOrNoLongerAuthorizedAccess(String scenario) throws Exception {
        var login = loginLegacy();
        String access = new ObjectMapper().readTree(login.getContentAsString()).path("content").path("accessToken").asText();
        if (scenario.equals("expired")) {
            clock.now = clock.now.plusSeconds(900);
        } else if (scenario.equals("disabled")) {
            jdbc.update("UPDATE member SET account_status = 'DISABLED' WHERE id = 42");
        } else if (scenario.equals("tampered")) {
            int signatureStart = access.lastIndexOf('.') + 1;
            access = access.substring(0, signatureStart) + (access.charAt(signatureStart) == 'a' ? 'b' : 'a') + access.substring(signatureStart + 1);
        } else {
            var claims = new java.util.HashMap<String, Object>();
            claims.put("sub", "42");
            claims.put("iat", clock.now.getEpochSecond());
            claims.put("nbf", clock.now.getEpochSecond());
            claims.put("exp", clock.now.plusSeconds(900).getEpochSecond());
            claims.put("jti", java.util.UUID.randomUUID().toString());
            if (scenario.equals("missing-claim")) { claims.remove("exp"); }
            if (scenario.equals("extra-claim")) { claims.put("LoginName", "TEST administrator"); }
            if (scenario.equals("future")) { claims.put("nbf", clock.now.plusSeconds(1).getEpochSecond()); }
            byte[] key = "integration-test-signing-key-at-least-32-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            access = scenario.equals("wrong-algorithm")
                    ? cn.hutool.jwt.JWT.create().addPayloads(claims).setSigner(cn.hutool.jwt.signers.JWTSignerUtil.hs512(key)).sign()
                    : cn.hutool.jwt.JWTUtil.createToken(claims, key);
        }
        mvc.perform(get("/web/identity/me").header("Authorization", "Bearer " + access)).andExpect(status().isUnauthorized());
    }

    @Test
    void heartbeatUsesTheAuthenticatedMemberWithoutPersistingAnAccessToken() throws Exception {
        String access = new ObjectMapper().readTree(loginLegacy().getContentAsString()).path("content").path("accessToken").asText();
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(get("/web/member/heart").header("Authorization", "Bearer " + access))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        }
        // 登录日志仅兼容历史活跃度统计，不得重新持久化原始访问令牌。
        assertNull(jdbc.queryForObject("SELECT token FROM member_login_log WHERE member_id = 42 ORDER BY id DESC LIMIT 1", String.class));
    }

    @Test
    void memberAccessTokenCannotAuthenticateAsAnAdministrator() throws Exception {
        clock.now = Instant.now();
        String access = new ObjectMapper().readTree(loginLegacy().getContentAsString()).path("content").path("accessToken").asText();
        mvc.perform(get("/admin/identity-probe").header("token", access)).andExpect(status().isUnauthorized());
    }

    private org.springframework.mock.web.MockHttpServletResponse loginLegacy() throws Exception {
        return mvc.perform(post("/web/identity/login").contentType("application/json")
                        .content("{\"email\":\"legacy@example.com\",\"password\":\"Test-password-123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
    }

    @Configuration
    @EnableWebMvc
    @EnableTransactionManagement
    @EnableAspectJAutoProxy
    @Import({IdentityConfiguration.class, IdentityController.class, IdentityExceptionHandler.class,
            SpringMvcConfig.class, WebLoginInterceptor.class, AdminLoginInterceptor.class, LogInterceptor.class, LogAspect.class,
            MemberController.class, MemberLoginLogService.class, ControllerExceptionHandler.class,
            com.jiawa.lyw.Util.JwtUtil.class, AdminProbeController.class})
    static class Config {
        // 公开身份端点不调用 DAU；受保护请求验证 Redis 不可用仍可鉴权，不写入任何缓存数据。
        @Bean
        LettuceConnectionFactory redisConnectionFactory() { return new LettuceConnectionFactory("127.0.0.1", 1); }

        @Bean
        StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory factory) { return new StringRedisTemplate(factory); }

        @Bean
        @Primary
        TestMailGateway testMailGateway() { return new TestMailGateway(); }

        @Bean
        @Primary
        TestClock testClock() { return new TestClock(); }

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(DATABASE.jdbcUrl(), DATABASE.username(), DATABASE.password());
        }

        @Bean
        SqlSessionFactoryBean sqlSessionFactory(DataSource dataSource) throws Exception {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            var resolver = new PathMatchingResourcePatternResolver();
            factory.setMapperLocations(java.util.stream.Stream.concat(
                    java.util.Arrays.stream(resolver.getResources("classpath:mapper/identity/*.xml")),
                    java.util.Arrays.stream(resolver.getResources("classpath:mapper/MemberLoginLogMapper.xml")))
                    .toArray(org.springframework.core.io.Resource[]::new));
            return factory;
        }

        @Bean
        MemberLoginLogMapper memberLoginLogMapper(org.apache.ibatis.session.SqlSessionFactory factory) {
            return new org.mybatis.spring.SqlSessionTemplate(factory).getMapper(MemberLoginLogMapper.class);
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }

    static class TestMailGateway implements IdentityMailGateway {
        String recipient;
        URI verificationLink;
        URI resetLink;
        boolean failDelivery;

        @Override public void sendVerificationLink(String email, URI link) {
            if (failDelivery) { throw new com.jiawa.lyw.Util.MailDeliveryException(); }
            recipient = email;
            verificationLink = link;
        }
        @Override public void sendPasswordResetLink(String email, URI link) {
            if (failDelivery) { throw new com.jiawa.lyw.Util.MailDeliveryException(); }
            recipient = email;
            resetLink = link;
        }
    }

    @org.springframework.web.bind.annotation.RestController
    static class AdminProbeController {
        @org.springframework.web.bind.annotation.GetMapping("/admin/identity-probe")
        java.util.Map<String, Boolean> probe() { return java.util.Map.of("reached", true); }
    }

    static class TestClock extends Clock {
        Instant now;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }
}
