package com.jiawa.lyw.controller.web;

import com.jiawa.lyw.req.*;
import com.jiawa.lyw.resp.CommonResp;
import com.jiawa.lyw.resp.PageResp;
import com.jiawa.lyw.resp.PostResp;
import com.jiawa.lyw.resp.PostDetailResp;
import com.jiawa.lyw.resp.PostFollowResp;
import com.jiawa.lyw.resp.PostInteractionResp;
import com.jiawa.lyw.resp.PostUserResp;
import com.jiawa.lyw.resp.PostViewerStateResp;
import com.jiawa.lyw.service.PostService;
import com.jiawa.lyw.service.PostDetailService;
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

    @Autowired
    private PostDetailService postDetailService;

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

    @GetMapping("/detail")
    public CommonResp<PostDetailResp> findPublicDetail(@RequestParam Long postId) {
        return new CommonResp<>(postDetailService.findPublicDetail(postId));
    }

    @GetMapping("/detail/viewer-state")
    public CommonResp<PostViewerStateResp> findViewerState(@RequestParam Long postId) {
        return new CommonResp<>(postDetailService.findViewerState(postId));
    }

    @PostMapping("/detail/like")
    public CommonResp<PostInteractionResp> setLike(@Valid @RequestBody PostInteractionReq req) {
        return new CommonResp<>(postDetailService.setLike(req.getPostId(), req.getActive()));
    }

    @PostMapping("/detail/favorite")
    public CommonResp<PostInteractionResp> setFavorite(@Valid @RequestBody PostInteractionReq req) {
        return new CommonResp<>(postDetailService.setFavorite(req.getPostId(), req.getActive()));
    }

    @PostMapping("/detail/follow")
    public CommonResp<PostFollowResp> setFollow(@Valid @RequestBody PostInteractionReq req) {
        return new CommonResp<>(postDetailService.setFollow(req.getPostId(), req.getActive()));
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

    @PostMapping("/post-list-Favorite-Posts")
    public CommonResp<List<PostResp>> listFavoritePostsByUserId(@Valid @RequestBody UserPostQueryReq req){
        System.out.println("收到的 userId = " + req.getUserid());
        List<PostResp> list = postService.listFavoritePostsByUserId(req);
        return new CommonResp<>(list);
    }


}
