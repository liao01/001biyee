package com.jiawa.lyw.controller.web;

import com.jiawa.lyw.req.UserActionReq;
import com.jiawa.lyw.resp.CommonResp;
import com.jiawa.lyw.resp.PostFavoriteResp;
import com.jiawa.lyw.resp.UserActionResp;
import com.jiawa.lyw.service.UserActionTypeService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/web/userAction")
public class UserActionTypeController {
    @Autowired
    private UserActionTypeService userActionTypeService;

    @PostMapping("/insertUserAction")
    public CommonResp<Object> insertUserAction(@Valid @RequestBody UserActionReq req){
        userActionTypeService.insertUserAction(req);
        return new CommonResp<>();
    }

    @PostMapping("/delUserAction")
    public CommonResp<Object> delUserAction(@Valid @RequestBody UserActionReq req){
        userActionTypeService.deleteUserAction(req);
        return new CommonResp<>();
    }

    @PostMapping("/findUserAction")
    public CommonResp<List<UserActionResp>> findUserAction(@Valid @RequestBody UserActionReq req){
        List<UserActionResp> userAction = userActionTypeService.findUserAction(req);
        return new CommonResp<>(userAction);
    }

    @PostMapping("/PostUserLikeActionCount")
    public CommonResp<Object> PostUserLikeActionCount(@Valid @RequestBody UserActionReq req){
        Integer count = userActionTypeService.PostUserLikeActionCount(req);
        return new CommonResp<>(count);
    }

    @PostMapping("/PostUserFavoritedcountActionCount")
    public CommonResp<Object> PostUserFavoritedcountActionCount(@Valid @RequestBody UserActionReq req){
        Integer count = userActionTypeService.PostUserFavoritedcountActionCount(req);
        return new CommonResp<>(count);
    }

    @GetMapping("/favorite")
    public CommonResp<List<PostFavoriteResp>> getFavoritePosts(){
        List<PostFavoriteResp> favoritePosts = userActionTypeService.getFavoritePosts();
        return new CommonResp<>(favoritePosts);
    }


}
