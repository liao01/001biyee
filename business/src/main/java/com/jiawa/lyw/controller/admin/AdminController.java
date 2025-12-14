package com.jiawa.lyw.controller.admin;

import cn.hutool.crypto.digest.DigestUtil;
import com.jiawa.lyw.enums.SmsCodeUseEnum;
import com.jiawa.lyw.req.UserLoginReq;
import com.jiawa.lyw.req.UserRegisterReq;
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
    private KaptchaService kaptchaService;



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
