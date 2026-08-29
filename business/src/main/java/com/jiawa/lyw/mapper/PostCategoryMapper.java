package com.jiawa.lyw.mapper;

import com.jiawa.lyw.domain.PostCategory;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PostCategoryMapper {
    List<PostCategory> selectEnabled();

    PostCategory selectByCode(@Param("code") String code);
}
