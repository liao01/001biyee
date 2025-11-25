package com.jiawa.lyw.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum PostStatusEnum {
    DELETE("2","删除"),
    OPEN("1","公开"),
    GRASS("0","草稿");

    @Getter
    private String code;
    @Getter
    private String desc;
}
