package com.jiawa.lyw.controller.web;

import com.jiawa.lyw.context.LoginMemberContext;
import com.jiawa.lyw.req.UserProfileReq;
import com.jiawa.lyw.resp.CommonResp;
import com.jiawa.lyw.resp.UserProfileResp;
import com.jiawa.lyw.service.UserProFileService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@Slf4j
@RestController
@RequestMapping("/web/UserProFile")
public class UserProFileController {
    @Autowired
    private UserProFileService userProFileService;

    @PostMapping("/save")
    public CommonResp<Object> insertUserProfile(@Valid @RequestBody UserProfileReq req){
        userProFileService.insertUserProfile(req);
        return new CommonResp<>();
    }

    @GetMapping("/findAvatarUser")
    public CommonResp<String> findAvatarUser(@RequestParam(required = false) Long id){
        String avatarUser;

        if (id == null) {
            avatarUser = userProFileService.findAvatarUser(LoginMemberContext.getId());
        } else {
            avatarUser = userProFileService.findAvatarUser(id);
        }

        return new CommonResp<>(avatarUser);
    }

    @GetMapping("/findAllUser")
    public CommonResp<List<UserProfileResp>> findAllUser(@RequestParam(required = false) Long userId){
        List<UserProfileResp> allFileUser;
        if (userId == null) {
            allFileUser = userProFileService.findAllFileUser(LoginMemberContext.getId());
        } else {
            allFileUser = userProFileService.findAllFileUser(userId);
        }
        return new CommonResp<>(allFileUser);
    }
}
