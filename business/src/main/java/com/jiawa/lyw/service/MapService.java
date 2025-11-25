package com.jiawa.lyw.service;

import com.alibaba.fastjson.JSONObject;
import com.jiawa.lyw.Util.AmapUtils;
import com.jiawa.lyw.domain.LocationRecord;
import com.jiawa.lyw.exception.BusinessException;
import com.jiawa.lyw.exception.BusinessExceptionEnum;
import com.jiawa.lyw.mapper.LocationRecordMapper;
import com.jiawa.lyw.mapper.LocationRecordMapperCust;
import com.jiawa.lyw.req.AddressDelReq;
import com.jiawa.lyw.req.AddressReq;
import com.jiawa.lyw.resp.LocationRecordResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class MapService {

    @Autowired
    private AmapUtils amapUtils;

    @Autowired
    private LocationRecordMapper locationRecordMapper;

    @Autowired
    private LocationRecordMapperCust locationRecordMapperCust;

    /**
     * 根据地址获取地理信息，并插入或更新到数据库
     */
    public void getGeo(AddressReq req) {

        log.info("插入或更新开始景区地图");

        String address = req.getAddress();
        Integer id = req.getId();

        // 调用高德API获取位置信息
        JSONObject location = amapUtils.getLocationByAddress(address);

        // 如果解析失败，直接抛异常
        if (location == null) {
            throw new BusinessException(BusinessExceptionEnum.Map_NOT_ERROR);
        }

        // 构建实体对象
        LocationRecord record = buildLocationRecord(location);
        record.setCreateTime(new Date());

        // 如果有ID则更新，否则插入
        if (id != null) {
            log.info("更新开始景区地图");
            record.setId(Long.valueOf(id));
            locationRecordMapper.updateByPrimaryKeySelective(record);
            log.info("更新结束景区地图");
        } else {
            log.info("插入开始景区地图");
            locationRecordMapper.insert(record);
            log.info("插入结束景区地图");
        }
    }

    /**
     * 封装高德返回数据到实体对象
     */
    private LocationRecord buildLocationRecord(JSONObject location) {
        LocationRecord record = new LocationRecord();
        record.setFormattedAddress(location.getString("formatted_address"));
        record.setLongitude(Double.parseDouble(location.getString("longitude")));
        record.setLatitude(Double.parseDouble(location.getString("latitude")));
        record.setProvince(location.getString("province"));
        record.setCity(location.getString("city"));
        record.setDistrict(location.getString("district"));
        return record;
    }

    /**
     * 获取所有位置信息
     */
    public List<LocationRecordResp> getLocationList() {
        return locationRecordMapperCust.findAll();
    }
    /**
     * 根据id获取更新到数据库
     */
    public void deleteLocation(AddressDelReq req) {
        log.info("删除景区地址开始",req.getId());
        Integer id = req.getId();
        if (id == null) {
            throw new BusinessException(BusinessExceptionEnum.Map_NOT_ERROR);
        }
        locationRecordMapper.deleteByPrimaryKey(Long.valueOf(id));
        log.info("删除景区地址结束",req.getId());
    }
}
