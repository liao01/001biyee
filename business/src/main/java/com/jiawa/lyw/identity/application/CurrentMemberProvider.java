package com.jiawa.lyw.identity.application;

/** 提供当前已验证会员，不向业务模块暴露 HTTP 或令牌结构。 */
public interface CurrentMemberProvider {
    long memberId();
}
