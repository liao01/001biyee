package com.jiawa.lyw.mapper;

import com.jiawa.lyw.domain.LocationImage;
import com.jiawa.lyw.domain.LocationImageExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface LocationImageMapper {
    long countByExample(LocationImageExample example);

    int deleteByExample(LocationImageExample example);

    int deleteByPrimaryKey(Long id);

    int insert(LocationImage record);

    int insertSelective(LocationImage record);

    List<LocationImage> selectByExample(LocationImageExample example);

    LocationImage selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") LocationImage record, @Param("example") LocationImageExample example);

    int updateByExample(@Param("record") LocationImage record, @Param("example") LocationImageExample example);

    int updateByPrimaryKeySelective(LocationImage record);

    int updateByPrimaryKey(LocationImage record);
}