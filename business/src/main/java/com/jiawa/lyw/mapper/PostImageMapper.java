package com.jiawa.lyw.mapper;

import com.jiawa.lyw.domain.PostImage;
import com.jiawa.lyw.domain.PostImageExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface PostImageMapper {
    long countByExample(PostImageExample example);

    int deleteByExample(PostImageExample example);

    int deleteByPrimaryKey(Long id);

    int insert(PostImage record);

    int insertSelective(PostImage record);

    List<PostImage> selectByExample(PostImageExample example);

    PostImage selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") PostImage record, @Param("example") PostImageExample example);

    int updateByExample(@Param("record") PostImage record, @Param("example") PostImageExample example);

    int updateByPrimaryKeySelective(PostImage record);

    int updateByPrimaryKey(PostImage record);
}