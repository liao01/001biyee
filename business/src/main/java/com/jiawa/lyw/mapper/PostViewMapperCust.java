package com.jiawa.lyw.mapper;

import com.jiawa.lyw.resp.PostViewResp;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostViewMapperCust {
    /**
     * 查询用户最近浏览的帖子（去重，仅保留最新一次浏览）
     * @param userId 用户ID
     * @return 浏览记录列表
     */
    List<PostViewResp> selectRecentViewedPosts(@Param("userId") Long userId);
}
