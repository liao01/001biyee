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


    /**
     * 保存评论信息
     *
     * @param req 评论请求对象，包含需要保存的评论信息
     * @return 包含服务端正式评论的通用响应对象
     */
    @PostMapping("/save-comment")
    public CommonResp<CommentResp> saveComment(@RequestBody @Valid CommentReq req) {
        return new CommonResp<>(commentService.saveComment(req));
    }

    @GetMapping("/findall-comment")
    public CommonResp<List<CommentResp>> findAllComment(@RequestParam Long postId) {
        CommentfinndReq req = new CommentfinndReq();
        req.setPostId(postId);
        List<CommentResp> comment = commentService.findComment(req);
        return new CommonResp<>(comment);
    }

    @PostMapping("/del-comment")
    public CommonResp<String> del(@RequestBody CommentDelReq req){
        return new CommonResp<>(commentService.deleteComment(req));
    }

    @PostMapping("/update-comment")
    public CommonResp<CommentResp> updateComment(@RequestBody @Valid CommentReq req) {
        return new CommonResp<>(commentService.updateComment(req));
    }
}
