package com.jiawa.lyw.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserRegisterReq {

    /**
     * 用户名
     */
    @NotBlank(message = "【用户名】不能为空")
    private String UserName;

    /**
     * 密码
     */
    @NotBlank(message = "【密码】不能为空")
    private String password;

    /**
     * 验证码
     */
    @NotBlank(message = "【验证码】不能为空")
    private String code;

}
