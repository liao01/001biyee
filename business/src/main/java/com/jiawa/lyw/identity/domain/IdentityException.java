package com.jiawa.lyw.identity.domain;

/** 消息必须为面向用户的固定文案，不附带输入、令牌或凭据。 */
public final class IdentityException extends RuntimeException {
    public IdentityException(String message) {
        super(message);
    }
}
