package com.jiawa.lyw.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import com.jiawa.lyw.context.LoginMemberContext;
import com.jiawa.lyw.domain.UserAction;
import com.jiawa.lyw.domain.UserActionExample;
import com.jiawa.lyw.enums.UserActionTypeEnum;
import com.jiawa.lyw.mapper.UserActionMapper;
import com.jiawa.lyw.mapper.UserActionMapperCust;
import com.jiawa.lyw.req.UserActionReq;
import com.jiawa.lyw.resp.PostFavoriteResp;
import com.jiawa.lyw.resp.UserActionResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class UserActionTypeService {
    @Autowired
    private UserActionMapper userActionMapper;

    @Autowired
    private UserActionMapperCust userActionMapperCust;

    public void insertUserAction(UserActionReq req) {
        Long userId = LoginMemberContext.getId();
        Long postId = req.getPostId();
        String actionType = req.getActionType();

        if (Objects.equals(actionType, UserActionTypeEnum.LIKE.getCode())){
            log.info("点赞插入语法开始,{}",LoginMemberContext.getId());
            insertSingleAction(userId, postId, UserActionTypeEnum.LIKE);
            log.info("点赞插入语法结束,{}",LoginMemberContext.getId());
        }else if (Objects.equals(actionType, UserActionTypeEnum.FAVORITE.getCode())){
            log.info("收藏插入语法开始,{}",LoginMemberContext.getId());
            insertSingleAction(userId, postId, UserActionTypeEnum.FAVORITE);
            log.info("收藏插入语法结束,{}",LoginMemberContext.getId());
        }else {
            log.info("收藏+点赞插入语法开始,{}",LoginMemberContext.getId());
            insertSingleAction(userId, postId, UserActionTypeEnum.LIKE);
            insertSingleAction(userId, postId, UserActionTypeEnum.FAVORITE);
            log.info("收藏+点赞插入语法结束,{}",LoginMemberContext.getId());
        }
    }
    /**
     * 插入单条用户行为记录
     */
    private void insertSingleAction(Long userId, Long postId, UserActionTypeEnum type) {
        UserAction userAction = new UserAction();
        userAction.setId(IdUtil.getSnowflakeNextId());
        userAction.setUserId(userId);
        userAction.setPostId(postId);
        userAction.setActionType(type.getCode());
        userAction.setCreateTime(new Date());
        userActionMapper.insert(userAction);
    }

    //查询自己的点赞收藏
    public List<UserActionResp> findUserAction(UserActionReq req) {
        log.info("查询点赞或收藏开始,{}",LoginMemberContext.getId());

        UserActionExample userActionExample = new UserActionExample();
        UserActionExample.Criteria criteria = userActionExample.createCriteria();
        criteria.andUserIdEqualTo(LoginMemberContext.getId());
        criteria.andPostIdEqualTo(req.getPostId());

        List<UserAction> userActions = userActionMapper.selectByExample(userActionExample);

        List<UserActionResp> userActionResps = BeanUtil.copyToList(userActions, UserActionResp.class);
        log.info("查询点赞或收藏结束,{}",LoginMemberContext.getId());
        return userActionResps;
    }

    //删除自己的点赞收藏
    public void deleteUserAction(UserActionReq req) {
        log.info("删除开始点赞或收藏,{}",LoginMemberContext.getId());

        Long userId = LoginMemberContext.getId();
        Long postId = req.getPostId();
        String actionType = req.getActionType();
        UserActionExample userActionExample = new UserActionExample();
        UserActionExample.Criteria criteria = userActionExample.createCriteria();
        criteria.andUserIdEqualTo(userId);
        criteria.andPostIdEqualTo(postId);

        if (Objects.equals(actionType, UserActionTypeEnum.LIKE.getCode())){
            log.info("点赞删除语法开始,{}",LoginMemberContext.getId());
            criteria.andActionTypeEqualTo(UserActionTypeEnum.LIKE.getCode());
            userActionMapper.deleteByExample(userActionExample);
            log.info("点赞删除语法结束,{}",LoginMemberContext.getId());
        }else if (Objects.equals(actionType, UserActionTypeEnum.FAVORITE.getCode())){
            log.info("收藏删除语法开始,{}",LoginMemberContext.getId());
            criteria.andActionTypeEqualTo(UserActionTypeEnum.FAVORITE.getCode());
            userActionMapper.deleteByExample(userActionExample);
            log.info("收藏删除语法结束,{}",LoginMemberContext.getId());
        }
        log.info("删除结束点赞或收藏,{}",LoginMemberContext.getId());
    }

    public Integer PostUserLikeActionCount(UserActionReq req) {
        log.info("查询Post点赞数,{}",LoginMemberContext.getId());
        UserActionExample userActionExample = new UserActionExample();
        UserActionExample.Criteria criteria = userActionExample.createCriteria();
        criteria.andPostIdEqualTo(req.getPostId());
        criteria.andActionTypeEqualTo(UserActionTypeEnum.LIKE.getCode());
        long l = userActionMapper.countByExample(userActionExample);
        return Math.toIntExact(l);
    }

    public Integer PostUserFavoritedcountActionCount(UserActionReq req) {
        log.info("查询Post收藏数,{}",LoginMemberContext.getId());
        UserActionExample userActionExample = new UserActionExample();
        UserActionExample.Criteria criteria = userActionExample.createCriteria();
        criteria.andPostIdEqualTo(req.getPostId());
        criteria.andActionTypeEqualTo(UserActionTypeEnum.FAVORITE.getCode());
        long l = userActionMapper.countByExample(userActionExample);
        return Math.toIntExact(l);
    }

    /**
     * 查询当前用户收藏的全部数据
     *
     * @return
     */
    public List<PostFavoriteResp> getFavoritePosts(){
        log.info("查询当前用户收藏的全部数据,{}",LoginMemberContext.getId());
        List<PostFavoriteResp> list = userActionMapperCust.selectFavoritePostsByUserId(LoginMemberContext.getId());
        return list;
    }
}
