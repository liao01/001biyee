package com.jiawa.lyw.controller.admin;

import cn.hutool.crypto.digest.DigestUtil;
import com.jiawa.lyw.req.*;
import com.jiawa.lyw.resp.CommonResp;
import com.jiawa.lyw.resp.PageResp;
import com.jiawa.lyw.resp.UserLoginResp;
import com.jiawa.lyw.resp.UserResp;
import com.jiawa.lyw.service.AdminService;
import com.jiawa.lyw.service.KaptchaService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


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

    @GetMapping("/query")
    public CommonResp<PageResp<UserResp>> query(@Valid PageReq req) {
        PageResp<UserResp> pageResp = adminService.selectUser(req);// 注意加分号并定义 result
        return new CommonResp<>(pageResp);
    }

    @PostMapping("/register")
    public CommonResp<Object> Register(@Valid @RequestBody UserRegisterReq req) {
        req.setPassword(DigestUtil.md5Hex(req.getPassword().toLowerCase()));

        log.info("用户注册开始:{}",req.getLoginName());

        // 校验图片验证码，防止短信攻击，不加的话，只能防止同一手机攻击，加上图片验证码，可防止不同的手机号攻击
        kaptchaService.validCode(req.getImageCode(), req.getImageCodeToken());
        log.info("用户验证码校验通过:{}",req.getLoginName());

        adminService.register(req);
        return new CommonResp<>();
    }

    @PostMapping("/forget")
    public CommonResp<Object> Forget(@Valid @RequestBody UserForgetReq req) {
        req.setPassword(DigestUtil.md5Hex(req.getPassword().toLowerCase()));

        log.info("用户重置密码开始:{}",req.getId());

        // 校验图片验证码，防止短信攻击，不加的话，只能防止同一手机攻击，加上图片验证码，可防止不同的手机号攻击
        kaptchaService.validCode(req.getImageCode(), req.getImageCodeToken());
        log.info("用户验证码校验通过:{}",req.getId());

        adminService.forget(req);
        return new CommonResp<>();
    }

    @PostMapping("/delete")
    public CommonResp<Object> Delete(@Valid @RequestBody UserDeleteReq req) {

        log.info("用户删除开始:{}",req.getId());

        // 校验图片验证码，防止短信攻击，不加的话，只能防止同一手机攻击，加上图片验证码，可防止不同的手机号攻击
        kaptchaService.validCode(req.getImageCode(), req.getImageCodeToken());
        log.info("用户验证码校验通过:{}",req.getId());

        adminService.delete(req);
        return new CommonResp<>();
    }

}
