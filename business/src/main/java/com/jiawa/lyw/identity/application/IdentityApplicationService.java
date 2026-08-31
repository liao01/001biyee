package com.jiawa.lyw.identity.application;

import com.jiawa.lyw.identity.domain.SessionTokens;

/** 身份模块正式用例入口；调用方不访问内部持久化和令牌实现。 */
public interface IdentityApplicationService {
    void register(String email, String rawPassword);

    void verifyEmail(String rawToken);

    SessionTokens login(String email, String rawPassword);

    SessionTokens refresh(String rawRefreshToken);

    void logout(String rawRefreshToken);

    void requestPasswordReset(String email);

    void resetPassword(String rawToken, String newRawPassword);
}
