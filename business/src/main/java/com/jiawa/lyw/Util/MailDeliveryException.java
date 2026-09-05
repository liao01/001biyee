package com.jiawa.lyw.Util;

/** 不附加邮件提供商异常，防止地址、授权信息或邮件原文进入错误日志。 */
public final class MailDeliveryException extends RuntimeException {
    public MailDeliveryException() { super("邮件服务暂时不可用"); }
}
