package com.jiawa.lyw.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum UserFollowStatusEnum {
    FOLLOW((byte)1, "已关注"),
    UNFOLLOW((byte)0, "未关注");

    @Getter
    private final byte code;

    @Getter
    private final String desc;
}
