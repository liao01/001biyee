package com.jiawa.lyw.controller.web;

import com.jiawa.lyw.req.AddressDelReq;
import com.jiawa.lyw.req.AddressReq;
import com.jiawa.lyw.resp.CommonResp;
import com.jiawa.lyw.resp.LocationRecordResp;
import com.jiawa.lyw.service.MapService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/web/location")//该接口前端未实现该接口中的updateLocaltion，getGeo，delLocaltion的方法，在管理员界面实现
public class LocationController {
    @Autowired
    private MapService mapService;

    @PostMapping("/geocode")
    public CommonResp<Object> getGeo(@Valid @RequestBody AddressReq req) {//管理员要是实现的功能之一
        mapService.getGeo(req);
        return new CommonResp<>();
    }

    @PostMapping("/updateLocaltion")
    public CommonResp<Object> updateLocaltion(@Valid @RequestBody AddressReq req) {//管理员要是实现的功能之一
        mapService.getGeo(req);
        return new CommonResp<>();
    }

    @PostMapping("/delLocaltion")
    public CommonResp<Object> delLocaltion(@Valid @RequestBody AddressDelReq req) {//管理员要是实现的功能之一
        mapService.deleteLocation(req);
        return new CommonResp<>();
    }

    @GetMapping("/findLocationAll")
    public CommonResp<List<LocationRecordResp>> findLocationAll(){
        List<LocationRecordResp> list = mapService.getLocationList();
        return new CommonResp<>(list);
    }
}
