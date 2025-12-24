package com.jiawa.lyw.mapper;

import com.jiawa.lyw.resp.LocationRecordResp;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LocationRecordMapperCust {
    List<LocationRecordResp> findAll();
    List<LocationRecordResp> searchLocation(@Param("keyword") String keyword);
}
