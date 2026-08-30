package com.jiawa.lyw.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jiawa.lyw.Util.JwtUtil;
import com.jiawa.lyw.context.LoginMemberContext;
import com.jiawa.lyw.context.LoginUserContext;
import com.jiawa.lyw.domain.MemberLoginLog;
import com.jiawa.lyw.interceptor.AdminLoginInterceptor;
import com.jiawa.lyw.interceptor.WebLoginInterceptor;
import com.jiawa.lyw.mapper.MemberLoginLogMapper;
import com.jiawa.lyw.resp.MemberLoginResp;
import com.jiawa.lyw.resp.UserLoginResp;
import com.jiawa.lyw.service.MemberLoginLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SensitiveLoggingTests {

    private static final String SIGNING_SECRET = "logging-test-signing-secret-at-least-32-bytes";

    @AfterEach
    void clearLoginContext() {
        LoginMemberContext.removeMember();
        LoginUserContext.removeUser();
    }

    @Test
    void adminAuthenticationRejectsATokenWithAnInvalidSignature() throws Exception {
        new JwtUtil(SIGNING_SECRET);
        String token = JwtUtil.createLoginToken(Map.of(
                "id", 42L,
                "LoginName", "administrator"
        ));
        char replacement = token.endsWith("a") ? 'b' : 'a';
        String tamperedToken = token.substring(0, token.length() - 1) + replacement;

        AdminLoginInterceptor interceptor = new AdminLoginInterceptor();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(requestWithToken(tamperedToken), response, new Object()));
        assertEquals(401, response.getStatus());
    }

    @Test
    void authenticationContextsAreClearedAfterRequestCompletion() throws Exception {
        MemberLoginResp member = new MemberLoginResp();
        member.setId(42L);
        LoginMemberContext.setMember(member);
        UserLoginResp user = new UserLoginResp();
        user.setId(7L);
        LoginUserContext.setUser(user);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/lyw/protected");
        MockHttpServletResponse response = new MockHttpServletResponse();
        new WebLoginInterceptor().afterCompletion(request, response, new Object(), new RuntimeException("handler failed"));
        new AdminLoginInterceptor().afterCompletion(request, response, new Object(), new RuntimeException("handler failed"));

        assertNull(LoginMemberContext.getMember());
        assertNull(LoginUserContext.getUser());
    }

    @Test
    void webAndAdminAuthenticationLogsNeverContainTheRawToken() throws Exception {
        new JwtUtil(SIGNING_SECRET);
        String token = JwtUtil.createLoginToken(Map.of(
                "id", 42L,
                "name", "traveler",
                "LoginName", "administrator"
        ));

        ListAppender<ILoggingEvent> webLogs = capture(WebLoginInterceptor.class);
        WebLoginInterceptor webInterceptor = new WebLoginInterceptor();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> sets = mock(SetOperations.class);
        when(redis.opsForSet()).thenReturn(sets);
        when(sets.add(any(), any())).thenReturn(1L);
        ReflectionTestUtils.setField(webInterceptor, "stringRedisTemplate", redis);

        MockHttpServletRequest webRequest = requestWithToken(token);
        assertTrue(webInterceptor.preHandle(webRequest, new MockHttpServletResponse(), new Object()));
        assertTokenAbsent(webLogs, token);

        ListAppender<ILoggingEvent> adminLogs = capture(AdminLoginInterceptor.class);
        AdminLoginInterceptor adminInterceptor = new AdminLoginInterceptor();
        MockHttpServletRequest adminRequest = requestWithToken(token);
        assertTrue(adminInterceptor.preHandle(adminRequest, new MockHttpServletResponse(), new Object()));
        assertTokenAbsent(adminLogs, token);
    }

    @Test
    void memberLoginAndHeartbeatLogsNeverContainTheRawToken() {
        String token = "sensitive-member-session-token";
        MemberLoginResp member = new MemberLoginResp();
        member.setId(42L);
        member.setName("traveler");
        member.setToken(token);

        MemberLoginLogMapper mapper = mock(MemberLoginLogMapper.class);
        MemberLoginLog existing = new MemberLoginLog();
        existing.setHeartCount(0);
        when(mapper.selectByExample(any())).thenReturn(List.of(existing));

        MemberLoginLogService service = new MemberLoginLogService();
        ReflectionTestUtils.setField(service, "memberLoginLogMapper", mapper);
        ListAppender<ILoggingEvent> logs = capture(MemberLoginLogService.class);

        service.save(member);
        LoginMemberContext.setMember(member);
        service.upadteHeartInfo();

        assertTokenAbsent(logs, token);
    }

    private static MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/lyw/protected");
        request.addHeader("token", token);
        return request;
    }

    private static ListAppender<ILoggingEvent> capture(Class<?> source) {
        Logger logger = (Logger) LoggerFactory.getLogger(source);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static void assertTokenAbsent(ListAppender<ILoggingEvent> logs, String token) {
        assertFalse(logs.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.contains(token)));
    }
}
