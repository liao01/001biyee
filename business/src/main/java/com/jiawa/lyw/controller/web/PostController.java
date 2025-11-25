package com.jiawa.lyw.controller.web;

import com.jiawa.lyw.req.*;
import com.jiawa.lyw.resp.CommonResp;
import com.jiawa.lyw.resp.PageResp;
import com.jiawa.lyw.resp.PostResp;
import com.jiawa.lyw.resp.PostUserResp;
import com.jiawa.lyw.service.PostService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/web/post")
public class PostController {
    @Autowired
    private PostService postService;

    @PostMapping("/post-save")
    public CommonResp<Object> savePost(@Valid @RequestBody PostReq req) throws IOException {
        System.out.println(req.getUserId());
        postService.savePost(req);
        return new CommonResp<>();
    }


    @GetMapping("/post-findAll")
    public CommonResp<List<PostResp>> findAll(){
        List<PostResp> list = postService.findAll();
        return new CommonResp<>(list);
    }

    @GetMapping("/post-search")
    public CommonResp<List<PostResp>> searchPosts(@ModelAttribute PostSearchReq req){
        List<PostResp> list = postService.searchPostsByKeyword(req);
        return new CommonResp<>(list);
    }

    @GetMapping("/post-User-search")
    public CommonResp<PageResp<PostUserResp>> searchPosts(@Valid PageReq req){
        PageResp<PostUserResp> list = postService.selectPostDetailsByUserId(req);
        return new CommonResp<>(list);
    }

    @PostMapping("/post-del")
    public CommonResp<Object> searchPosts(@Valid @RequestBody DelPostReq req){
        postService.del(req);
        return new CommonResp<>();
    }

    @PostMapping("/post-UserPostQuery")
    public CommonResp<List<PostResp>> selectPostDetailsByPostId(@Valid @RequestBody UserPostQueryReq req){
        System.out.println("收到的 userId = " + req.getUserid());
        List<PostResp> list = postService.selectPostDetailsByPostId(req);
        return new CommonResp<>(list);
    }


}
