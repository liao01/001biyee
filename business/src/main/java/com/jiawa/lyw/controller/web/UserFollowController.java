package com.jiawa.lyw.controller.web;

import com.jiawa.lyw.req.PageReq;
import com.jiawa.lyw.req.UserFollowReq;
import com.jiawa.lyw.resp.CommonResp;
import com.jiawa.lyw.resp.PageResp;
import com.jiawa.lyw.resp.StatisticResp;
import com.jiawa.lyw.resp.UserFollowPesp;
import com.jiawa.lyw.service.UserFollowService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/web/userfollow")
public class UserFollowController {
    @Autowired
    private UserFollowService userFollowService;

    @PostMapping("/save-user-follow")
    public CommonResp<Object> saveUserFollow(@Valid @RequestBody UserFollowReq req) {
        userFollowService.handleUserFollow(req,true);
        return new CommonResp<>();
    }
    @PostMapping("/unfollow-user-follow")
    public CommonResp<Object> unfollow(@RequestBody UserFollowReq req) {
        userFollowService.handleUserFollow(req, false);
        return new CommonResp<>();
    }
    @PostMapping("/find-user-follow")
    public CommonResp<Boolean> isFindUserFollow(@Valid @RequestBody UserFollowReq req) {
        boolean followed = userFollowService.isFollowed(req);
        return new CommonResp<>(followed);
    }

    @GetMapping("/query-statistic")
    public CommonResp<StatisticResp> getUserFollowTrendLast30Days(){
        StatisticResp userFollowTrendLast30Days = userFollowService.getUserFollowTrendLast30Days();
        return new CommonResp<>(userFollowTrendLast30Days);
    }
    @GetMapping("/query-ByUserIdList")
    public CommonResp<PageResp<UserFollowPesp>> getFollowingListByUserIdList(@Valid PageReq req){
        PageResp<UserFollowPesp> pageResp = userFollowService.getFollowingListByUserIdList(req);
        return new CommonResp<>(pageResp);
    }

    @GetMapping("/byUserIds")
    public CommonResp<Integer> getFollowerCount(@RequestParam(required = false)  Long userId){
        Integer followerCount = userFollowService.getFollowerCount(userId);
        return new CommonResp<>(followerCount);
    }
}
