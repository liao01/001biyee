package com.jiawa.lyw.mapper;

import com.jiawa.lyw.resp.UserProfileResp;
import org.apache.ibatis.annotations.Select;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserProfileMapperCust {
    @Select("SELECT avatar FROM user_profile WHERE user_id = #{id}")
    String selectAvatarById(@Param("id") Long id);

    List<UserProfileResp> selectUserProfile(@Param("userId") Long userId);
}