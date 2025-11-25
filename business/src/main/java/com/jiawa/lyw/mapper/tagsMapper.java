package com.jiawa.lyw.mapper;

import com.jiawa.lyw.domain.tags;
import com.jiawa.lyw.domain.tagsExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface tagsMapper {
    long countByExample(tagsExample example);

    int deleteByExample(tagsExample example);

    int deleteByPrimaryKey(Long id);

    int insert(tags record);

    int insertSelective(tags record);

    List<tags> selectByExample(tagsExample example);

    tags selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") tags record, @Param("example") tagsExample example);

    int updateByExample(@Param("record") tags record, @Param("example") tagsExample example);

    int updateByPrimaryKeySelective(tags record);

    int updateByPrimaryKey(tags record);
}