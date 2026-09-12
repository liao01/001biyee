package com.jiawa.lyw.identity.infrastructure;

import com.jiawa.lyw.Util.MailUtils;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public final class VerificationLinkMailer implements IdentityMailGateway {
    private final MailUtils mail;

    public VerificationLinkMailer(MailUtils mail) { this.mail = mail; }

    @Override
    public void sendVerificationLink(String email, URI link) {
        mail.sendMail(email, "请在链接有效期内验证邮箱：<a href=\"" + escaped(link) + "\">验证邮箱</a>。如果不是本人操作，请忽略此邮件。", "旅分享 · 验证邮箱");
    }

    @Override
    public void sendPasswordResetLink(String email, URI link) {
        mail.sendMail(email, "请在链接有效期内设置新密码：<a href=\"" + escaped(link) + "\">重置密码</a>。如果不是本人操作，请忽略此邮件。", "旅分享 · 重置密码");
    }

    private String escaped(URI link) {
        return link.toASCIIString().replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }
}
