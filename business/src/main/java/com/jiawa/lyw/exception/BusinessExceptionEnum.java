package com.jiawa.lyw.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum BusinessExceptionEnum {
    DEMO_MOBILE_NOT_NULL("手机号不能为空！！!"),

    SMS_CODE_TOO_FREQUENT("短信请求过于频繁"),
    SMS_CODE_ERROR("短信验证码不正确"),
    SMS_CODE_EXPIRED("短信验证码未发送或已过期，请重新发送短信"),

    MEMBER_MOBILE_HAD_REGISTER("手机号已注册"),
    MEMBER_MOBILE_NOT_REGISTER("手机号未注册"),
    MEMBER_LOGIN_ERROR("手机号未注册或密码错误"),

    COMMENT_CONTENT_IN("评论内容已存在!!"),
    COMMENT_CONTENT_NOT("帖子不存在!!"),
    COMMENT_CONTENT_PARAM_ERROR("评论ID不能为空!!"),

    USER_CANNOT_FOLLOW_SELF("关注失败!!"),
    ALREADY_FOLLOWED("已关注，不能重复关注"),
    PARAM_ERROR("用户为空，不能重复关注"),
    DATA_NOT_FOUND("用户为空，不能重复关注"),

    Map_NOT_ERROR("地址上传失败"),

    IMAGE_CODE_ERROR("图片验证码不正确"),
    IMAGE_NOT_ERROR("图片上传失败"),
    IMAGE_NO_ERROR("图片未找到"),
    INVALID_GENDER("性别参数错误"),
    POST_ID_EMPTY("贴子为找到");


    @Getter
    private final String desc;
}
