package com.jiawa.lyw.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Date;

@Data
public class UserProfileReq {


    @NotNull(message = "图片不能为空")
    private String avatar;

    private Byte gender;

    @NotNull(message = "生日不能为空")
    private Date birthday;

    private String bio;

    @NotNull(message = "用户名不能为空")
    private String username;

    private String location;

}