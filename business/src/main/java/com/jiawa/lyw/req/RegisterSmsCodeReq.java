package com.jiawa.lyw.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterSmsCodeReq {
    @NotBlank(message = "【手机号】不能为空")
    private String mobile;

    @NotBlank(message = "【图片验证码】不能为空")
    private String imageCode;

    @NotBlank(message = "【图片验证码】参数非法")
    private String imageCodeToken;


}
