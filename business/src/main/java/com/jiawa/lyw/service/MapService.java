package com.jiawa.lyw.service;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson.JSONObject;
import com.jiawa.lyw.Util.AmapUtils;
import com.jiawa.lyw.domain.LocationImage;
import com.jiawa.lyw.domain.LocationRecord;
import com.jiawa.lyw.exception.BusinessException;
import com.jiawa.lyw.exception.BusinessExceptionEnum;
import com.jiawa.lyw.mapper.LocationImageMapper;
import com.jiawa.lyw.mapper.LocationRecordMapper;
import com.jiawa.lyw.mapper.LocationRecordMapperCust;
import com.jiawa.lyw.req.AddressReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;


@Service
@Slf4j
public class MapService {

    @Autowired
    private AmapUtils amapUtils;

    @Autowired
    private LocationRecordMapper locationRecordMapper;

    @Autowired
    private LocationRecordMapperCust locationRecordMapperCust;

    @Autowired
    private LocationImageMapper locationImageMapper;

    private static final String UPLOAD_DIR = "D:/idea/lyw/uploads/location/";

    /**
     * 根据地址获取地理信息，并插入或更新到数据库
     */
    @Transactional
    public Long createLocation(AddressReq req, MultipartFile[] files) {

        JSONObject location = amapUtils.getLocationByAddress(req.getFormattedAddress());
        if (location == null) {
            throw new BusinessException(BusinessExceptionEnum.Map_NOT_ERROR);
        }

        Long locationId = IdUtil.getSnowflakeNextId();

        LocationRecord record = buildLocationRecord(location);
        record.setId(locationId);
        record.setName(req.getName());
        record.setDescription(req.getDescription());

        locationRecordMapper.insert(record);

        if (files != null && files.length > 0) {
            uploadImages(locationId, files, req.getDescription());
        }

        return locationId;
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


    @Transactional
    public List<String> uploadImages(Long locationId,
                                     MultipartFile[] files,
                                     String description) {

        if (files == null || files.length == 0) {
            throw new BusinessException(BusinessExceptionEnum.IMAGE_NOT_ERROR);
        }

        List<String> urls = new ArrayList<>();

        try {
            for (int i = 0; i < files.length; i++) {
                MultipartFile file = files[i];
                String url = saveSingleImage(
                        locationId,
                        file,
                        i + 1,              // seq 自动排序
                        description
                );
                urls.add(url);
            }
            return urls;

        } catch (Exception e) {
            throw new BusinessException(BusinessExceptionEnum.IMAGE_NOT_ERROR);
        }
    }


    private String saveSingleImage(Long locationId,
                                   MultipartFile file,
                                   Integer seq,
                                   String description) throws Exception {

        String originalFilename = file.getOriginalFilename();
        String suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
        String fileName = UUID.randomUUID() + suffix;

        File dir = new File(UPLOAD_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File dest = new File(UPLOAD_DIR + fileName);
        file.transferTo(dest);

        String imageUrl = "/upload/location/" + fileName;

        LocationImage image = new LocationImage();
        image.setId(IdUtil.getSnowflakeNextId());
        image.setLocationId(locationId);
        image.setImageUrl(imageUrl);
        image.setSeq(seq);
        image.setDescription(description);
        image.setCreateTime(new Date());

        locationImageMapper.insert(image);

        return imageUrl;
    }

}
