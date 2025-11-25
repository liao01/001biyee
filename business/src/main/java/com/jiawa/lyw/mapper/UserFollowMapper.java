package com.jiawa.lyw.mapper;

import com.jiawa.lyw.domain.UserFollow;
import com.jiawa.lyw.domain.UserFollowExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface UserFollowMapper {
    long countByExample(UserFollowExample example);

    int deleteByExample(UserFollowExample example);

    int deleteByPrimaryKey(Long id);

    int insert(UserFollow record);

    int insertSelective(UserFollow record);

    List<UserFollow> selectByExample(UserFollowExample example);

    UserFollow selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") UserFollow record, @Param("example") UserFollowExample example);

    int updateByExample(@Param("record") UserFollow record, @Param("example") UserFollowExample example);

    int updateByPrimaryKeySelective(UserFollow record);

    int updateByPrimaryKey(UserFollow record);
}