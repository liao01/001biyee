package com.jiawa.lyw.identity.infrastructure;

import java.net.URI;

/** 外部邮件边界；实现不得记录收件链接或底层邮件凭据。 */
public interface IdentityMailGateway {
    void sendVerificationLink(String email, URI link);
    void sendPasswordResetLink(String email, URI link);
}
