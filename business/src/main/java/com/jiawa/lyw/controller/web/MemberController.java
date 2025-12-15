package com.jiawa.lyw.controller.web;

import cn.hutool.crypto.digest.DigestUtil;
import com.jiawa.lyw.enums.SmsCodeUseEnum;
import com.jiawa.lyw.req.MemberLoginReq;
import com.jiawa.lyw.req.MemberRegisterReq;
import com.jiawa.lyw.req.MemberResetReq;
import com.jiawa.lyw.resp.CommonResp;
import com.jiawa.lyw.resp.MemberLoginResp;
import com.jiawa.lyw.service.KaptchaService;
import com.jiawa.lyw.service.MemberLoginLogService;
import com.jiawa.lyw.service.MemberService;
import com.jiawa.lyw.service.SmsCodeService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/web/member")
public class MemberController {
    @Autowired
    private MemberService memberService;

    @Autowired
    private SmsCodeService smsCodeService;

    @Autowired
    private KaptchaService kaptchaService;

    @Autowired
    private MemberLoginLogService memberLoginLogService;

    @PostMapping("/register")
    public CommonResp<Object> Register(@Valid @RequestBody MemberRegisterReq req) {
        req.setPassword(DigestUtil.md5Hex(req.getPassword().toLowerCase()));

        log.info("会员注册开始:{}",req.getMobile());

        smsCodeService.validCode(req.getMobile(), SmsCodeUseEnum.REGISTER.getCode(), req.getCode());
        log.info("注册验证码校验通过:{}",req.getMobile());

        memberService.register(req);
        return new CommonResp<>();
    }

    @GetMapping("/heart")
    public CommonResp<Object> heart() {
        memberLoginLogService.upadteHeartInfo();
        return new CommonResp<>();
    }

    @PostMapping("/reset")
    public CommonResp<Object> reset(@Valid @RequestBody MemberResetReq req) {
        req.setPassword(DigestUtil.md5Hex(req.getPassword().toLowerCase()));

        log.info("会员重置密码开始:{}",req.getMobile());

        smsCodeService.validCode(req.getMobile(), SmsCodeUseEnum.RESET.getCode(), req.getCode());
        log.info("重置密码验证码校验通过:{}",req.getMobile());

        memberService.reset(req);
        return new CommonResp<>();
    }


    @PostMapping("/login")
    public CommonResp<MemberLoginResp> login(@Valid @RequestBody MemberLoginReq req) {
        req.setPassword(DigestUtil.md5Hex(req.getPassword().toLowerCase()));

        log.info("会员登录开始:{}",req.getMobile());

        // 校验图片验证码，防止短信攻击，不加的话，只能防止同一手机攻击，加上图片验证码，可防止不同的手机号攻击
        kaptchaService.validCode(req.getImageCode(), req.getImageCodeToken());

        MemberLoginResp loginResp = memberService.login(req);
        return new CommonResp<>(loginResp);
    }

}
