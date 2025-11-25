package com.jiawa.lyw.service;

import cn.hutool.core.util.IdUtil;
import com.jiawa.lyw.context.LoginMemberContext;
import com.jiawa.lyw.domain.Comment;
import com.jiawa.lyw.domain.CommentExample;
import com.jiawa.lyw.exception.BusinessException;
import com.jiawa.lyw.exception.BusinessExceptionEnum;
import com.jiawa.lyw.mapper.CommentMapper;
import com.jiawa.lyw.mapper.CommentMapperCust;
import com.jiawa.lyw.req.CommentDelReq;
import com.jiawa.lyw.req.CommentReq;
import com.jiawa.lyw.req.CommentfinndReq;
import com.jiawa.lyw.resp.CommentResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class CommentService {
    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private CommentMapperCust  commentMapperCust;

    public void saveComment(CommentReq req) {
        log.info("评论保存开始:{}"+LoginMemberContext.getId());
        CommentExample example = new CommentExample();
        CommentExample.Criteria criteria = example.createCriteria();
        criteria.andPostIdEqualTo(req.getPostId())  // 用帖子ID
                .andContentEqualTo(req.getContent()); // 用内容判断重复
        long count = commentMapper.countByExample(example);

        if (count > 0) {
            log.warn("评论重复内容:{}"+LoginMemberContext.getId());
            throw new BusinessException(BusinessExceptionEnum.COMMENT_CONTENT_IN);
        }

        Comment comment = new Comment();
        comment.setId(IdUtil.getSnowflakeNextId());
        comment.setPostId(req.getPostId());
        comment.setUserId(LoginMemberContext.getId());
        comment.setContent(req.getContent());
        comment.setCreateTime(new Date());

        commentMapper.insert(comment);
        log.info("评论保存结束:{}"+LoginMemberContext.getId());
    }

    public List<CommentResp> findComment(CommentfinndReq req) {
        log.info("根据帖子查找全部评论开始: {}", req.getPostId());

        List<CommentResp> comments = commentMapperCust.findCommentByPostId(req.getPostId());

        if (comments == null) {
            comments = new ArrayList<>();
        }

        log.info("根据帖子查找全部评论结束: {}", req.getPostId());
        return comments;
    }

    public void deleteComment(CommentDelReq req) {
        log.info("删除评论开始: {}", req.getId());
        commentMapper.deleteByPrimaryKey(req.getId());
        log.info("删除评论结束: {}", req.getId());
    }

    public void updateComment(CommentReq req) {
        log.info("修改评论开始: {}", req.getId());

        if (req.getId() == null) {
            throw new BusinessException(BusinessExceptionEnum.COMMENT_CONTENT_PARAM_ERROR);
        }

        CommentExample commentExample = new CommentExample();
        commentExample.createCriteria().andIdEqualTo(req.getId());

        long count = commentMapper.countByExample(commentExample);
        if (count == 0) {
            throw new BusinessException(BusinessExceptionEnum.COMMENT_CONTENT_NOT);
        }

        Comment comment = new Comment();
        comment.setContent(req.getContent());
        comment.setCreateTime(new Date());

        // 推荐使用 selective 避免覆盖其他字段
        commentMapper.updateByExampleSelective(comment, commentExample);

        log.info("修改评论结束: {}", req.getId());
    }


}
