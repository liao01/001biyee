package com.jiawa.lyw.controller.web;

import com.jiawa.lyw.req.RegisterSmsCodeReq;
import com.jiawa.lyw.req.ResetSmsCodeReq;
import com.jiawa.lyw.resp.CommonResp;
import com.jiawa.lyw.service.KaptchaService;
import com.jiawa.lyw.service.SmsCodeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/** 历史短信接口兼容审计代码：不注册 Spring Controller，禁止重新接入运行流程。 */
@Deprecated
@RequestMapping("/web/sms-code")
public class SmsCodeController {
    @Autowired
    private SmsCodeService smsCodeService;

    @Autowired
    private KaptchaService kaptchaService;

    @PostMapping("/send-for-register")
    public CommonResp<Object> sendForRegister(@Valid @RequestBody RegisterSmsCodeReq req) {
        // 校验图片验证码，防止短信攻击，不加的话，只能防止同一手机攻击，加上图片验证码，可防止不同的手机号攻击
        kaptchaService.validCode(req.getImageCode(), req.getImageCodeToken());

        smsCodeService.sendCodeForRegister(req.getMobile());
        return new CommonResp<>();
    }
    @PostMapping("/send-for-reset")
    public CommonResp<Object> sendForReset(@Valid @RequestBody ResetSmsCodeReq req) {
        // 校验图片验证码，防止短信攻击，不加的话，只能防止同一手机攻击，加上图片验证码，可防止不同的手机号攻击
        kaptchaService.validCode(req.getImageCode(), req.getImageCodeToken());

        smsCodeService.sendCodeForReset(req.getMobile());
        return new CommonResp<>();
    }

}
