package com.jiawa.lyw.interceptor;

import com.jiawa.lyw.context.LoginMemberContext;
import com.jiawa.lyw.identity.application.CurrentMemberProvider;
import com.jiawa.lyw.identity.domain.IdentityException;
import com.jiawa.lyw.resp.MemberLoginResp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.method.HandlerMethod;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.dao.DataAccessException;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class WebLoginInterceptor implements HandlerInterceptor {
    @Autowired
    private CurrentMemberProvider currentMember;
    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        LoginMemberContext.removeMember();
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        try {
            // 显式旧业务兼容桥接；正式身份事实源是 CurrentMemberProvider，不复制令牌。
            MemberLoginResp member = new MemberLoginResp();
            member.setId(currentMember.memberId());
            LoginMemberContext.setMember(member);
            recordDailyActivity(member.getId());
            return true;
        } catch (IdentityException ignored) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("Cache-Control", "no-store");
            return false;
        }
    }

    private void recordDailyActivity(long memberId) {
        if (stringRedisTemplate == null) { return; }
        try {
            String key = "dau:" + LocalDate.now();
            stringRedisTemplate.opsForSet().add(key, Long.toString(memberId));
            stringRedisTemplate.expire(key, 7, TimeUnit.DAYS);
        } catch (DataAccessException ignored) {
            log.warn("活跃度缓存暂时不可用，身份校验不受影响");
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        LoginMemberContext.removeMember();
    }
}
