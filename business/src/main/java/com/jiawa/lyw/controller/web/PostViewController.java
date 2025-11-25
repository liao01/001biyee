package com.jiawa.lyw.controller.web;

import com.jiawa.lyw.req.PostViewReq;
import com.jiawa.lyw.resp.CommonResp;
import com.jiawa.lyw.resp.PostViewResp;
import com.jiawa.lyw.service.PostViewService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/web/postview")
public class PostViewController {
    @Autowired
    private PostViewService postViewService;

    @PostMapping("/save")
    public CommonResp<Object> PostViewSave(@Valid @RequestBody PostViewReq postViewReq){
        postViewService.save(postViewReq);
        return new CommonResp<>();
    }

    @GetMapping("/find")
    public CommonResp<List<PostViewResp>> PostViewFind(){
        List<PostViewResp> all = postViewService.findAll();
        return new CommonResp<>(all);
    }
}
