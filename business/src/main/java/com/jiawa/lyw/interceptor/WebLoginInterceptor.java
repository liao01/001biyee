package com.jiawa.lyw.interceptor;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.jiawa.lyw.Util.JwtUtil;
import com.jiawa.lyw.context.LoginMemberContext;
import com.jiawa.lyw.resp.MemberLoginResp;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import java.util.concurrent.TimeUnit;
import java.time.LocalDate;

@Slf4j
@Component
public class WebLoginInterceptor implements HandlerInterceptor {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // OPTIONS请求不做校验,
        // 前后端分离的架构, 前端会发一个OPTIONS请求先做预检, 对预检请求不做校验
        if(request.getMethod().toUpperCase().equals("OPTIONS")){
            return true;
        }

        String path = request.getRequestURL().toString();
        log.info("接口登录拦截，path：{}", path);

        //获取header的token参数
        String token = request.getHeader("token");
        log.info("网站登录验证开始");
        if (token == null || token.isEmpty()) {
            log.info( "token为空，请求被拦截" );
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        } else if (!JwtUtil.validate(token)) {
            log.info( "token校验不通过，请求被拦截" );
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        } else {
            log.info("会员登录验证通过");
            JSONObject loginMember = JwtUtil.getJSONObject(token);
            log.info("当前登录会员：{}", loginMember);
            MemberLoginResp member = JSONUtil.toBean(loginMember, MemberLoginResp.class);
            member.setToken(token);
            LoginMemberContext.setMember(member);

            // ================== DAU 统计开始 ==================
            Long memberId = member.getId();
            String today = LocalDate.now().toString(); // 2025-12-16
            String key = "dau:" + today;

            stringRedisTemplate.opsForSet().add(key, String.valueOf(memberId));
            stringRedisTemplate.expire(key, 7, TimeUnit.DAYS);
            // ================== DAU 统计结束 ==================

            return true;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        LoginMemberContext.removeMember();
    }
}
