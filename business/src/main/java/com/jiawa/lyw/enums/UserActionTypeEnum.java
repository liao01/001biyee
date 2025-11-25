package com.jiawa.lyw.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor

public enum UserActionTypeEnum {
    LIKE("like", "点赞"),
    FAVORITE("favorite", "收藏");

    @Getter
    private final String code;

    @Getter
    private final String desc;
}
