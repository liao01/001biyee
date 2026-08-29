package com.jiawa.lyw.mapper;

import com.jiawa.lyw.resp.PostResp;
import com.jiawa.lyw.resp.PostUserResp;
import com.jiawa.lyw.resp.StatisticDateResp;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface PostMapperCust {
    List<PostResp> findAll(@Param("view") String view, @Param("categoryCode") String categoryCode);
    PostResp findPublicDetailById(@Param("postId") Long postId);
    List<PostResp> searchPostsByKeyword(@Param("keyword") String keyword);
    List<PostUserResp> selectPostDetailsByUserId(@Param("userId") Long userId);
    List<PostResp> UserPostQuery(@Param("userId") Long userId);
    List<PostResp> listFavoritePostsByUserId(@Param("userId") Long userId);
    List<StatisticDateResp> selectDailyPostCountLast30Days();
    @Update("UPDATE post SET status = #{status} WHERE id = #{postId}")
    int updateStatus(@Param("postId") Long postId, @Param("status") String status);
}
