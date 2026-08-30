package com.jiawa.lyw.interceptor;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.jiawa.lyw.Util.JwtUtil;
import com.jiawa.lyw.context.LoginUserContext;
import com.jiawa.lyw.resp.UserLoginResp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class AdminLoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    //OPTIONS请求不做校验
        //前后端分离架构，前端会发一个OPTIONS请求先做预检，对预检不做校验
        if (request.getMethod().toUpperCase().equals("OPTIONS")) {
            return true;
        }

        String path = request.getRequestURL().toString();
        log.info("接口登录拦截，path：{}", path);

//获取header的token参数
        String token = request.getHeader("token");
        log.info("控台登录验证开始");
        if (token == null || token.isEmpty()) {
            log.info( "token为空，请求被拦截" );
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        } else {
            try {
                if (!JwtUtil.validate(token)) {
                    log.info("token校验不通过，请求被拦截");
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    return false;
                }
                JSONObject loginUser = JwtUtil.getJSONObject(token);//通过JwtUtil看看解不解析的出来
                UserLoginResp user = JSONUtil.toBean(loginUser, UserLoginResp.class);//这个是刚刚解析的时JSON格式，这个将JSON格式转化为实体类
                LoginUserContext.setUser(user);//最后获得了实体类我们就去在线程的本地变量去放
                log.info("用户登录验证通过，用户id：{}", user.getId());
                return true;
            } catch (RuntimeException exception) {
                log.info("token格式非法，请求被拦截");
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                return false;
            }
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        LoginUserContext.removeUser();
    }
}
