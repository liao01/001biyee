package com.jiawa.lyw.controller.web;

import com.jiawa.lyw.req.CommentDelReq;
import com.jiawa.lyw.req.CommentReq;
import com.jiawa.lyw.req.CommentfinndReq;
import com.jiawa.lyw.resp.CommentResp;
import com.jiawa.lyw.resp.CommonResp;
import com.jiawa.lyw.service.CommentService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/web/comment")
public class CommentController {
    @Autowired
    private CommentService commentService;


    @PostMapping("/save-comment")
    public CommonResp<Object> saveComment(@RequestBody @Valid CommentReq req) {
        commentService.saveComment(req);
        return new CommonResp<>();
    }

    @GetMapping("/findall-comment")
    public CommonResp<List<CommentResp>> findAllComment(@RequestParam Long postId) {
        CommentfinndReq req = new CommentfinndReq();
        req.setPostId(postId);
        List<CommentResp> comment = commentService.findComment(req);
        return new CommonResp<>(comment);
    }

    @PostMapping("/del-comment")
    public CommonResp<Object> del(@RequestBody CommentDelReq req){
        commentService.deleteComment(req);
        return new CommonResp<>();
    }

    @PostMapping("/update-comment")
    public CommonResp<Object> updateComment(@RequestBody @Valid CommentReq req) {
        commentService.updateComment(req);
        return new CommonResp<>();
    }
}
