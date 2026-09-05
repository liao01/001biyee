package com.jiawa.lyw.Util;

import java.util.Properties;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 邮件发送工具。邮箱账号和授权码只从运行环境读取。
 */
@Component
public final class MailUtils {
    private final String username;
    private final String authCode;

    public MailUtils(
            @Value("${mail.username}") String username,
            @Value("${mail.auth-code}") String authCode
    ) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("mail.username must not be blank");
        }
        if (authCode == null || authCode.isBlank()) {
            throw new IllegalArgumentException("mail.auth-code must not be blank");
        }
        this.username = username;
        this.authCode = authCode;
    }

    public boolean sendMail(String to, String text, String title) {
        Properties properties = new Properties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.host", "smtp.163.com");
        properties.put("mail.smtp.port", "465");
        properties.put("mail.smtp.ssl.enable", "true");
        properties.put("mail.smtp.ssl.checkserveridentity", "true");
        properties.put("mail.smtp.connectiontimeout", "5000");
        properties.put("mail.smtp.timeout", "5000");
        properties.put("mail.smtp.writetimeout", "5000");

        Authenticator authenticator = new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, authCode);
            }
        };

        try {
            Session session = Session.getInstance(properties, authenticator);
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(username));
            message.setRecipient(
                    Message.RecipientType.TO,
                    new InternetAddress(to)
            );
            message.setSubject(title);
            message.setContent(text, "text/html;charset=UTF-8");
            Transport.send(message);
            return true;
        } catch (MessagingException exception) {
            throw new MailDeliveryException();
        }
    }

}
