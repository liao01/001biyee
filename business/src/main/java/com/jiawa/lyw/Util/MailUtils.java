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

/**
 * 邮件发送工具。邮箱账号和授权码只从运行环境读取。
 */
public final class MailUtils {
    private static final String MAIL_USERNAME_ENV = "MAIL_USERNAME";
    private static final String MAIL_AUTH_CODE_ENV = "MAIL_AUTH_CODE";

    public MailUtils() {
    }

    public static boolean sendMail(String to, String text, String title) {
        String username = requiredEnvironmentValue(MAIL_USERNAME_ENV);
        String authCode = requiredEnvironmentValue(MAIL_AUTH_CODE_ENV);

        Properties properties = new Properties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.host", "smtp.163.com");
        properties.put("mail.smtp.port", "465");
        properties.put("mail.smtp.ssl.enable", "true");
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
            return false;
        }
    }

    private static String requiredEnvironmentValue(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing required environment variable: " + name
            );
        }
        return value;
    }
}
