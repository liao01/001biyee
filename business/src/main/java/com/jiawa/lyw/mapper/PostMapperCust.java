package com.jiawa.lyw.mapper;

import com.jiawa.lyw.resp.PostResp;
import com.jiawa.lyw.resp.PostUserResp;
import org.apache.ibatis.annotations.Update;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostMapperCust {
    List<PostResp> findAll();
    List<PostResp> searchPostsByKeyword(@Param("keyword") String keyword);
    List<PostUserResp> selectPostDetailsByUserId(@Param("id") Long id);
    List<PostResp> UserPostQuery(@Param("id") Long id);
    List<PostResp> listFavoritePostsByUserId(@Param("id") Long id);
    @Update("UPDATE post SET status = #{status} WHERE id = #{postId}")
    int updateStatus(@Param("postId") Long postId, @Param("status") String status);
}
