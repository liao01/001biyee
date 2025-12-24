package com.jiawa.lyw.controller.web;

import com.jiawa.lyw.resp.CommonResp;
import com.jiawa.lyw.resp.LocationRecordResp;
import com.jiawa.lyw.service.MapService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/web/location")//该接口前端未实现该接口中的updateLocaltion，getGeo，delLocaltion的方法，在管理员界面实现
public class LocationController {
    @Resource
    private MapService mapService;

    @GetMapping("/findLocationAll")
    public CommonResp<List<LocationRecordResp>> findAll(){
        List<LocationRecordResp> all = mapService.findAll();
        return new CommonResp<>(all);
    }

    @GetMapping("/searchLocation")
    public CommonResp<List<LocationRecordResp>> searchLocation(@RequestParam(required = false) String keyword){
        List<LocationRecordResp> all = mapService.searchLocation(keyword);
        return new CommonResp<>(all);
    }

}
