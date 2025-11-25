package com.jiawa.lyw.mapper;

import com.jiawa.lyw.domain.postTag;
import com.jiawa.lyw.domain.postTagExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface postTagMapper {
    long countByExample(postTagExample example);

    int deleteByExample(postTagExample example);

    int deleteByPrimaryKey(Long id);

    int insert(postTag record);

    int insertSelective(postTag record);

    List<postTag> selectByExample(postTagExample example);

    postTag selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") postTag record, @Param("example") postTagExample example);

    int updateByExample(@Param("record") postTag record, @Param("example") postTagExample example);

    int updateByPrimaryKeySelective(postTag record);

    int updateByPrimaryKey(postTag record);
}