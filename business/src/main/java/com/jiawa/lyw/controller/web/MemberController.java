package com.jiawa.lyw.controller.web;

import com.jiawa.lyw.resp.CommonResp;
import com.jiawa.lyw.service.MemberLoginLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/web/member")
public class MemberController {
    @Autowired
    private MemberLoginLogService memberLoginLogService;

    @GetMapping("/heart")
    public CommonResp<Object> heart() {
        memberLoginLogService.upadteHeartInfo();
        return new CommonResp<>();
    }
}
