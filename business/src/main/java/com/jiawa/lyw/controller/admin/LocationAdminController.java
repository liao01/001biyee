package com.jiawa.lyw.controller.admin;

import com.jiawa.lyw.req.AddressReq;
import com.jiawa.lyw.resp.CommonResp;
import com.jiawa.lyw.service.MapService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin/location")//该接口前端未实现该接口中的updateLocaltion，getGeo，delLocaltion的方法，在管理员界面实现
public class LocationAdminController {
    @Resource
    private MapService mapService;

    @PostMapping("/save")
    public CommonResp<Object> save(  @ModelAttribute AddressReq req,
                                     @RequestParam(value = "files", required = false) MultipartFile[] files){
        mapService.createLocation(req,files);
        return new CommonResp<>();
    };
}
