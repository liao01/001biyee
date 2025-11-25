package com.jiawa.lyw.mapper;

import com.jiawa.lyw.domain.PostView;
import com.jiawa.lyw.domain.PostViewExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface PostViewMapper {
    long countByExample(PostViewExample example);

    int deleteByExample(PostViewExample example);

    int deleteByPrimaryKey(Long id);

    int insert(PostView record);

    int insertSelective(PostView record);

    List<PostView> selectByExample(PostViewExample example);

    PostView selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") PostView record, @Param("example") PostViewExample example);

    int updateByExample(@Param("record") PostView record, @Param("example") PostViewExample example);

    int updateByPrimaryKeySelective(PostView record);

    int updateByPrimaryKey(PostView record);
}