package com.jiawa.lyw.controller;

import com.jiawa.lyw.req.DemoQueryReq;
import com.jiawa.lyw.resp.CommonResp;
import com.jiawa.lyw.resp.DemoQueryResp;
import com.jiawa.lyw.service.DemoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TestController {
    @Autowired
    private DemoService demoService;
    @GetMapping("/hello")
    public String hello(){
        return "hello";
    }

    @GetMapping("/count")
    public CommonResp<Integer> count(){
//        return demoService.count();
        return new CommonResp<>( demoService.count());
    }

    @GetMapping("/query")
    public CommonResp<List<DemoQueryResp>> query(@Valid DemoQueryReq req){
        List<DemoQueryResp> query = demoService.query(req);
//        CommonResp<List<Demo>> listCommonResp = new CommonResp<>();
//        listCommonResp.setContent(query);
//        return listCommonResp;

        return new CommonResp<>(query);
    }
}
