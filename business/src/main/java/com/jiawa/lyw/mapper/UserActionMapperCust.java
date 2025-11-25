package com.jiawa.lyw.mapper;

import com.jiawa.lyw.resp.PostFavoriteResp;

import java.util.List;

public interface UserActionMapperCust {
    List<PostFavoriteResp> selectFavoritePostsByUserId(Long userId);
}
