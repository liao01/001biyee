package com.jiawa.lyw.mapper;

import com.jiawa.lyw.domain.UserAction;
import com.jiawa.lyw.domain.UserActionExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface UserActionMapper {
    long countByExample(UserActionExample example);

    int deleteByExample(UserActionExample example);

    int deleteByPrimaryKey(Long id);

    int insert(UserAction record);

    int insertSelective(UserAction record);

    List<UserAction> selectByExample(UserActionExample example);

    UserAction selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") UserAction record, @Param("example") UserActionExample example);

    int updateByExample(@Param("record") UserAction record, @Param("example") UserActionExample example);

    int updateByPrimaryKeySelective(UserAction record);

    int updateByPrimaryKey(UserAction record);
}