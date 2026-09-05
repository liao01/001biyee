package com.jiawa.lyw.identity.domain;

/** 消息必须为面向用户的固定文案，不附带输入、令牌或凭据。 */
public final class IdentityException extends RuntimeException {
    private final Reason reason;

    public IdentityException(String message) {
        this(Reason.INVALID_INPUT, message);
    }

    public IdentityException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        INVALID_INPUT, UNAUTHENTICATED, EMAIL_NOT_VERIFIED, INVALID_TOKEN
    }
}
