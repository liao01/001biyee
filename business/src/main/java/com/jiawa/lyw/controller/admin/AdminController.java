package com.jiawa.lyw.controller.admin;

import cn.hutool.crypto.digest.DigestUtil;
import com.jiawa.lyw.req.UserLoginReq;
import com.jiawa.lyw.resp.CommonResp;
import com.jiawa.lyw.resp.UserLoginResp;
import com.jiawa.lyw.service.AdminService;
import com.jiawa.lyw.service.KaptchaService;
import com.jiawa.lyw.service.SmsCodeService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@Slf4j
@RestController
@RequestMapping("/admin/member")
public class AdminController {
    @Autowired
    private AdminService adminService;

    @Autowired
    private SmsCodeService smsCodeService;

    @Autowired
    private KaptchaService kaptchaService;

//    @PostMapping("/register")
//    public CommonResp<Object> Register(@Valid @RequestBody MemberRegisterReq req) {
//        req.setPassword(DigestUtil.md5Hex(req.getPassword().toLowerCase()));
//
//        log.info("会员注册开始:{}",req.getMobile());
//
//        smsCodeService.validCode(req.getMobile(), SmsCodeUseEnum.REGISTER.getCode(), req.getCode());
//        log.info("注册验证码校验通过:{}",req.getMobile());
//
//        adminService.register(req);
//        return new CommonResp<>();
//    }

//    @PostMapping("/reset")
//    public CommonResp<Object> reset(@Valid @RequestBody MemberResetReq req) {
//        req.setPassword(DigestUtil.md5Hex(req.getPassword().toLowerCase()));
//
//        log.info("会员重置密码开始:{}",req.getMobile());
//
//        smsCodeService.validCode(req.getMobile(), SmsCodeUseEnum.RESET.getCode(), req.getCode());
//        log.info("重置密码验证码校验通过:{}",req.getMobile());
//
//        adminService.reset(req);
//        return new CommonResp<>();
//    }


    @PostMapping("/login")
    public CommonResp<UserLoginResp> login(@Valid @RequestBody UserLoginReq req){
        req.setPassword(DigestUtil.md5Hex(req.getPassword().toLowerCase()));

        // 校验图片验证码，防止短信攻击，不加的话，只能防止同一手机攻击，加上图片验证码，可防止不同的手机号攻击
        kaptchaService.validCode(req.getImageCode(), req.getImageCodeToken());

        log.info("用户登录开始:{}",req.getLoginName());
        UserLoginResp userLoginResp = adminService.login(req);


        return new CommonResp<>(userLoginResp);
    }

}
