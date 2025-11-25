package com.jiawa.lyw.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum SmsCodeUseEnum {
    REGISTER("0","注册"),
    RESET("1","重置密码");

    @Getter
    private String code;
    @Getter
    private String desc;
}
