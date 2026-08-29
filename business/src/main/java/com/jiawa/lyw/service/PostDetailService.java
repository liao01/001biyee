package com.jiawa.lyw.service;

import cn.hutool.core.util.IdUtil;
import com.jiawa.lyw.context.LoginMemberContext;
import com.jiawa.lyw.domain.Post;
import com.jiawa.lyw.domain.PostImage;
import com.jiawa.lyw.domain.PostImageExample;
import com.jiawa.lyw.domain.UserAction;
import com.jiawa.lyw.domain.UserActionExample;
import com.jiawa.lyw.domain.UserFollow;
import com.jiawa.lyw.domain.UserFollowExample;
import com.jiawa.lyw.enums.PostStatusEnum;
import com.jiawa.lyw.enums.UserActionTypeEnum;
import com.jiawa.lyw.enums.UserFollowStatusEnum;
import com.jiawa.lyw.exception.BusinessException;
import com.jiawa.lyw.exception.BusinessExceptionEnum;
import com.jiawa.lyw.mapper.CommentMapperCust;
import com.jiawa.lyw.mapper.PostImageMapper;
import com.jiawa.lyw.mapper.PostMapper;
import com.jiawa.lyw.mapper.PostMapperCust;
import com.jiawa.lyw.mapper.UserActionMapper;
import com.jiawa.lyw.mapper.UserFollowMapper;
import com.jiawa.lyw.resp.CommentResp;
import com.jiawa.lyw.resp.PostDetailResp;
import com.jiawa.lyw.resp.PostFollowResp;
import com.jiawa.lyw.resp.PostInteractionResp;
import com.jiawa.lyw.resp.PostResp;
import com.jiawa.lyw.resp.PostViewerStateResp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
public class PostDetailService {
    @Autowired
    private PostMapper postMapper;

    @Autowired
    private PostMapperCust postMapperCust;

    @Autowired
    private PostImageMapper postImageMapper;

    @Autowired
    private CommentMapperCust commentMapperCust;

    @Autowired
    private UserActionMapper userActionMapper;

    @Autowired
    private UserFollowMapper userFollowMapper;

    public PostDetailResp findPublicDetail(Long postId) {
        PostResp source = postMapperCust.findPublicDetailById(postId);
        if (source == null) {
            throw new BusinessException(BusinessExceptionEnum.POST_ID_EMPTY);
        }

        PostImageExample imageExample = new PostImageExample();
        imageExample.createCriteria().andPostIdEqualTo(postId);
        imageExample.setOrderByClause("seq ASC");
        List<String> images = postImageMapper.selectByExample(imageExample).stream()
                .map(PostImage::getImageUrl)
                .toList();

        List<CommentResp> comments = commentMapperCust.findCommentByPostId(postId);
        if (comments == null) {
            comments = List.of();
        }

        PostDetailResp.PostContent post = new PostDetailResp.PostContent();
        post.setId(source.getPostId());
        post.setTitle(source.getPostTitle());
        post.setDescription(source.getPostContent());
        post.setPostTime(source.getPostTime());
        post.setCategoryCode(source.getCategoryCode());
        post.setCategoryName(source.getCategoryName());

        PostDetailResp.Author author = new PostDetailResp.Author();
        author.setId(source.getUserId());
        author.setName(source.getMembername());
        author.setAvatar(source.getAvatar());

        PostDetailResp.InteractionCounts counts = new PostDetailResp.InteractionCounts();
        counts.setLike(countActions(postId, UserActionTypeEnum.LIKE));
        counts.setFavorite(countActions(postId, UserActionTypeEnum.FAVORITE));

        PostDetailResp detail = new PostDetailResp();
        detail.setPost(post);
        detail.setAuthor(author);
        detail.setImages(images);
        detail.setComments(comments);
        detail.setInteractionCounts(counts);
        return detail;
    }

    public PostViewerStateResp findViewerState(Long postId) {
        Post post = requirePublicPost(postId);
        long viewerId = LoginMemberContext.getId();
        boolean selfAuthor = viewerId == post.getUserId();
        return new PostViewerStateResp(
                viewerId,
                hasAction(viewerId, postId, UserActionTypeEnum.LIKE),
                hasAction(viewerId, postId, UserActionTypeEnum.FAVORITE),
                !selfAuthor && isFollowing(viewerId, post.getUserId()),
                selfAuthor
        );
    }

    @Transactional
    public PostInteractionResp setLike(Long postId, boolean active) {
        return setAction(postId, UserActionTypeEnum.LIKE, active);
    }

    @Transactional
    public PostInteractionResp setFavorite(Long postId, boolean active) {
        return setAction(postId, UserActionTypeEnum.FAVORITE, active);
    }

    @Transactional
    public PostFollowResp setFollow(Long postId, boolean active) {
        Post post = requirePublicPost(postId);
        long viewerId = LoginMemberContext.getId();
        long authorId = post.getUserId();
        if (viewerId == authorId) {
            throw new BusinessException(BusinessExceptionEnum.USER_CANNOT_FOLLOW_SELF);
        }

        UserFollowExample relationship = followRelationship(viewerId, authorId);
        List<UserFollow> existing = userFollowMapper.selectByExample(relationship);
        if (existing.isEmpty()) {
            if (active) {
                UserFollow follow = new UserFollow();
                follow.setId(IdUtil.getSnowflakeNextId());
                follow.setUserId(viewerId);
                follow.setFollowId(authorId);
                follow.setCreateTime(new Date());
                follow.setStatus(UserFollowStatusEnum.FOLLOW.getCode());
                userFollowMapper.insert(follow);
            }
        } else {
            byte desiredStatus = active
                    ? UserFollowStatusEnum.FOLLOW.getCode()
                    : UserFollowStatusEnum.UNFOLLOW.getCode();
            boolean alreadyConfirmed = existing.stream()
                    .allMatch(follow -> Objects.equals(follow.getStatus(), desiredStatus));
            if (!alreadyConfirmed) {
                UserFollow changes = new UserFollow();
                changes.setStatus(desiredStatus);
                userFollowMapper.updateByExampleSelective(changes, relationship);
            }
        }

        return new PostFollowResp(isFollowing(viewerId, authorId));
    }

    private PostInteractionResp setAction(Long postId, UserActionTypeEnum actionType, boolean active) {
        requirePublicPost(postId);
        long viewerId = LoginMemberContext.getId();
        UserActionExample viewerAction = viewerAction(viewerId, postId, actionType);
        boolean current = userActionMapper.countByExample(viewerAction) > 0;

        if (active && !current) {
            UserAction action = new UserAction();
            action.setId(IdUtil.getSnowflakeNextId());
            action.setUserId(viewerId);
            action.setPostId(postId);
            action.setActionType(actionType.getCode());
            action.setCreateTime(new Date());
            userActionMapper.insert(action);
        } else if (!active && current) {
            userActionMapper.deleteByExample(viewerAction);
        }

        boolean confirmedActive = userActionMapper.countByExample(
                viewerAction(viewerId, postId, actionType)) > 0;
        return new PostInteractionResp(confirmedActive, countActions(postId, actionType));
    }

    private Post requirePublicPost(Long postId) {
        Post post = postMapper.selectByPrimaryKey(postId);
        if (post == null || !PostStatusEnum.OPEN.getCode().equals(post.getStatus())) {
            throw new BusinessException(BusinessExceptionEnum.POST_ID_EMPTY);
        }
        return post;
    }

    private boolean hasAction(long viewerId, long postId, UserActionTypeEnum actionType) {
        return userActionMapper.countByExample(viewerAction(viewerId, postId, actionType)) > 0;
    }

    private UserActionExample viewerAction(long viewerId, long postId, UserActionTypeEnum actionType) {
        UserActionExample example = new UserActionExample();
        example.createCriteria()
                .andUserIdEqualTo(viewerId)
                .andPostIdEqualTo(postId)
                .andActionTypeEqualTo(actionType.getCode());
        return example;
    }

    private long countActions(Long postId, UserActionTypeEnum actionType) {
        UserActionExample example = new UserActionExample();
        example.createCriteria()
                .andPostIdEqualTo(postId)
                .andActionTypeEqualTo(actionType.getCode());
        return userActionMapper.countByExample(example);
    }

    private boolean isFollowing(long viewerId, long authorId) {
        UserFollowExample example = new UserFollowExample();
        example.createCriteria()
                .andUserIdEqualTo(viewerId)
                .andFollowIdEqualTo(authorId)
                .andStatusEqualTo(UserFollowStatusEnum.FOLLOW.getCode());
        return userFollowMapper.countByExample(example) > 0;
    }

    private UserFollowExample followRelationship(long viewerId, long authorId) {
        UserFollowExample example = new UserFollowExample();
        example.createCriteria()
                .andUserIdEqualTo(viewerId)
                .andFollowIdEqualTo(authorId);
        return example;
    }
}
