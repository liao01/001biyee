package com.jiawa.lyw.mapper;

import com.jiawa.lyw.resp.CommentResp;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentMapperCust {
    List<CommentResp> findCommentByPostId(@Param("postId") Long postId);
}
