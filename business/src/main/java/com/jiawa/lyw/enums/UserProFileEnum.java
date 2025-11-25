package com.jiawa.lyw.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum UserProFileEnum {
    UNKNOWN("0", "未知"),
    MALE("1", "男"),
    FEMALE("2", "女");

    private final String code;
    private final String desc;

    public static UserProFileEnum fromCode(String code) {
        for (UserProFileEnum gender : values()) {
            if (gender.getCode().equals(code)) {
                return gender;
            }
        }
        return UNKNOWN;
    }
}
