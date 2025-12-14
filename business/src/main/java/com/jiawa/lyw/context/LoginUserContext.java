package com.jiawa.lyw.context;


import com.jiawa.lyw.resp.UserLoginResp;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LoginUserContext {//线程本地变量

    private static ThreadLocal<UserLoginResp> user = new ThreadLocal<>();

    public static void setUser(UserLoginResp user) {
        LoginUserContext.user.set(user);
    }

    public static UserLoginResp getUser() {
        return user.get();
    }

    public static void removeUser() {
        LoginUserContext.user.remove();
    }

    public static Long getId() {
        try {
            return user.get().getId();
        } catch (Exception e) {
            log.error("获取登录用户信息异常", e);
            throw e;
        }
    }

}
