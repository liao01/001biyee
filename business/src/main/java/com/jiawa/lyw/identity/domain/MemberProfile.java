package com.jiawa.lyw.identity.domain;

/** 当前会员最小展示信息；不包含凭据或历史登录标识。 */
public record MemberProfile(long id, String name) {
}
