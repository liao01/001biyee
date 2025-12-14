package com.jiawa.lyw.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserLoginReq {

    /**
     * 用户名
     */
    @NotBlank(message = "【用户名】不能为空")
    private String LoginName;

    /**
     * 密码
     */
    @NotBlank(message = "【密码】不能为空")
    private String password;



    @NotBlank(message = "【图片验证码】不能为空")
    private String imageCode;

    @NotBlank(message = "【图片验证码】参数非法")
    private String imageCodeToken;


}
