package com.jiawa.lyw.mapper;

import com.jiawa.lyw.resp.PostResp;
import com.jiawa.lyw.resp.PostUserResp;
import com.jiawa.lyw.resp.StatisticDateResp;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface PostMapperCust {
    List<PostResp> findAll();
    PostResp findPublicDetailById(@Param("postId") Long postId);
    List<PostResp> searchPostsByKeyword(@Param("keyword") String keyword);
    List<PostUserResp> selectPostDetailsByUserId(@Param("id") Long id);
    List<PostResp> UserPostQuery(@Param("id") Long id);
    List<PostResp> listFavoritePostsByUserId(@Param("id") Long id);
    List<StatisticDateResp> selectDailyPostCountLast30Days();
    @Update("UPDATE post SET status = #{status} WHERE id = #{postId}")
    int updateStatus(@Param("postId") Long postId, @Param("status") String status);
}
