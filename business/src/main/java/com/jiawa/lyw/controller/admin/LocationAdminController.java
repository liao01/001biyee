package com.jiawa.lyw.controller.admin;

import com.jiawa.lyw.req.AddressReq;
import com.jiawa.lyw.req.LocationDelReq;
import com.jiawa.lyw.resp.CommonResp;
import com.jiawa.lyw.resp.LocationRecordResp;
import com.jiawa.lyw.service.MapService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/admin/location")
public class LocationAdminController {
    @Resource
    private MapService mapService;

    @PostMapping("/save")
    public CommonResp<Object> save(  @ModelAttribute AddressReq req,
                                     @RequestParam(value = "files", required = false) MultipartFile[] files){
        mapService.createLocation(req,files);
        return new CommonResp<>();
    };

    @PostMapping("/del")
    public CommonResp<Object> delete(@Valid @RequestBody LocationDelReq req){
        mapService.deleteLocation(req);
        return new CommonResp<>();
    };

    @GetMapping("/findLocationAll")
    public CommonResp<List<LocationRecordResp>> findAll(){
        List<LocationRecordResp> all = mapService.findAll();
        return new CommonResp<>(all);
    }
}
